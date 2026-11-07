-- Todas las claves primarias pasan a ser enteros autoincrementales.
--
-- Hasta aquí los ids eran texto: cuid en las filas que creó Prisma y UUID en
-- las que creó el backend Java. Desde esta versión cada tabla numera sus filas
-- 1, 2, 3… con una columna identity de PostgreSQL, y las claves foráneas que
-- apuntan a ellas pasan también a bigint.
--
-- Los datos que ya había se renumeran en el orden en que se crearon (por su
-- fecha de creación cuando la tabla la tiene; si no, por el orden en que están
-- guardadas). Cada id viejo se traduce a su número nuevo con una tabla de
-- correspondencias, así que ninguna relación se pierde.
--
-- Se hace de forma genérica, leyendo el catálogo, en vez de tabla por tabla:
-- con 38 tablas y 60 claves foráneas, escribir cada ALTER a mano es la manera
-- más segura de olvidarse una.
--
-- Los QR de las entradas llevan el id firmado, así que cambian con él. No se
-- pueden volver a firmar aquí porque el secreto no está en la base: lo hace la
-- migración Java V7 justo después.

-- ── 1. Qué tablas se convierten ──────────────────────────────────────────
-- Las que tienen una clave primaria de una sola columna «id» de tipo texto.

create table _tabla_a_convertir (
    tabla text primary key,
    orden text not null
);

insert into _tabla_a_convertir (tabla, orden)
select c.relname,
       -- El orden en que se numeran las filas: la primera columna de fecha de
       -- creación que tenga la tabla; si no tiene ninguna, «orden» (zonas); y
       -- si tampoco, el orden físico, que es el de inserción.
       coalesce(
           (select format('%I', a2.attname)
              from unnest(array['creadoEn', 'creadaEn', 'emitidaEn', 'escaneadoEn', 'iniciadaEn',
                                'abiertaEn', 'ocurrioEn', 'fechaGasto', 'pagadaEn', 'empiezaEn',
                                'fechaAlta', 'orden'])
                   with ordinality as preferida(nombre, prioridad)
              join pg_attribute a2 on a2.attrelid = c.oid and a2.attname = preferida.nombre
                                  and not a2.attisdropped
             order by preferida.prioridad
             limit 1) || ', ctid',
           'ctid')
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
  join pg_constraint pk on pk.conrelid = c.oid and pk.contype = 'p'
  join pg_attribute a on a.attrelid = c.oid and a.attnum = pk.conkey[1]
 where c.relkind = 'r'
   and array_length(pk.conkey, 1) = 1
   and a.attname = 'id'
   and a.atttypid = 'text'::regtype;

-- ── 2. La correspondencia id viejo → número nuevo ────────────────────────

create table _mapa_id (
    tabla text   not null,
    viejo text   not null,
    nuevo bigint not null,
    primary key (tabla, viejo)
);

do $$
declare
    t record;
begin
    for t in select * from _tabla_a_convertir loop
        execute format(
            'insert into _mapa_id (tabla, viejo, nuevo)
             select %L, id, row_number() over (order by %s) from public.%I',
            t.tabla, t.orden, t.tabla);
    end loop;
end $$;

-- Una función y no una subconsulta: ALTER COLUMN … USING no admite
-- subconsultas, pero sí llamadas a funciones.
create function _nuevo_id(p_tabla text, p_viejo text) returns bigint
    language sql stable
    as $$ select nuevo from _mapa_id where tabla = p_tabla and viejo = p_viejo $$;

-- ── 3. Qué columna apunta a qué tabla ────────────────────────────────────
-- Cada columna que forma parte de una clave foránea hacia el «id» de una
-- tabla convertida. En la foránea compuesta Acceso(entradaId, eventoId) →
-- Entrada(id, eventoId), solo entradaId apunta a un id; eventoId se traduce
-- por su propia foránea hacia Evento.

create table _columna_foranea (
    tabla      text not null,
    columna    text not null,
    referencia text not null,
    primary key (tabla, columna)
);

insert into _columna_foranea (tabla, columna, referencia)
select distinct hija.relname, col.attname, madre.relname
  from pg_constraint fk
  join pg_class hija  on hija.oid = fk.conrelid
  join pg_class madre on madre.oid = fk.confrelid
  join _tabla_a_convertir conv on conv.tabla = madre.relname
  cross join lateral unnest(fk.conkey, fk.confkey) as par(hijo, padre)
  join pg_attribute col     on col.attrelid = fk.conrelid  and col.attnum = par.hijo
  join pg_attribute col_mad on col_mad.attrelid = fk.confrelid and col_mad.attnum = par.padre
 where fk.contype = 'f'
   and fk.connamespace = 'public'::regnamespace
   and col_mad.attname = 'id';

-- Si una columna apuntara a dos tablas distintas no sabríamos traducirla.
do $$
begin
    if exists (select 1 from _columna_foranea group by tabla, columna having count(*) > 1) then
        raise exception 'Hay columnas que apuntan a más de una tabla';
    end if;
end $$;

-- ── 4. Se guardan y se quitan las claves foráneas ────────────────────────

create table _foranea_guardada (
    tabla      text not null,
    nombre     text not null,
    definicion text not null
);

insert into _foranea_guardada (tabla, nombre, definicion)
select hija.relname, fk.conname, pg_get_constraintdef(fk.oid)
  from pg_constraint fk
  join pg_class hija  on hija.oid = fk.conrelid
  join pg_class madre on madre.oid = fk.confrelid
  join _tabla_a_convertir conv on conv.tabla = madre.relname
 where fk.contype = 'f'
   and fk.connamespace = 'public'::regnamespace;

do $$
declare
    f record;
begin
    for f in select * from _foranea_guardada loop
        execute format('alter table public.%I drop constraint %I', f.tabla, f.nombre);
    end loop;
end $$;

-- ── 5. Las referencias que van dentro de un texto ────────────────────────
-- El movimiento de stock de una venta de barra dice «Venta <id>», y el de una
-- orden de compra lleva su código «OC-XXXXXX», sacado del id. Se reescriben
-- antes de perder la correspondencia.

update "MovimientoStock" m
   set referencia = 'Venta ' || mapa.nuevo
  from _mapa_id mapa
 where mapa.tabla = 'VentaBarra'
   and m.referencia = 'Venta ' || mapa.viejo;

update "MovimientoStock" m
   set referencia = 'OC-' || lpad(mapa.nuevo::text, 4, '0')
  from _mapa_id mapa
 where mapa.tabla = 'OrdenCompra'
   and m.referencia = 'OC-' || upper(left(mapa.viejo, 6));

-- ── 6. Cambio de tipo: ids y foráneas a bigint ───────────────────────────
-- Un solo ALTER TABLE por tabla, con todas sus columnas: así se reescribe una
-- vez y no una por columna. Si un id no tuviera correspondencia, la función
-- devuelve null y la columna NOT NULL lo rechaza: ninguna relación se cambia
-- en silencio.

do $$
declare
    t        record;
    clausulas text[];
begin
    for t in
        select tabla from _tabla_a_convertir
        union
        select tabla from _columna_foranea
    loop
        clausulas := array[]::text[];

        if exists (select 1 from _tabla_a_convertir where tabla = t.tabla) then
            clausulas := clausulas || format(
                'alter column id type bigint using _nuevo_id(%L, id)', t.tabla);
        end if;

        select clausulas || coalesce(array_agg(format(
                   'alter column %I type bigint using _nuevo_id(%L, %I)',
                   f.columna, f.referencia, f.columna)), array[]::text[])
          into clausulas
          from _columna_foranea f
         where f.tabla = t.tabla;

        execute format('alter table public.%I %s', t.tabla, array_to_string(clausulas, ', '));
    end loop;
end $$;

-- ── 7. Las claves foráneas, otra vez ─────────────────────────────────────
-- Con la misma definición de antes: ahora las dos puntas son bigint.

do $$
declare
    f record;
begin
    for f in select * from _foranea_guardada loop
        execute format('alter table public.%I add constraint %I %s', f.tabla, f.nombre, f.definicion);
    end loop;
end $$;

-- ── 8. Autoincremento ────────────────────────────────────────────────────
-- BY DEFAULT y no ALWAYS: la base numera sola, pero acepta un id explícito.
-- Lo necesita la entrada, que reserva su número antes de insertarse para poder
-- firmar el QR con él.

do $$
declare
    t record;
    siguiente bigint;
begin
    for t in select tabla from _tabla_a_convertir loop
        execute format('alter table public.%I alter column id add generated by default as identity', t.tabla);
        execute format('select coalesce(max(id), 0) + 1 from public.%I', t.tabla) into siguiente;
        execute format('alter table public.%I alter column id restart with %s', t.tabla, siguiente);
    end loop;
end $$;

-- ── 9. Limpieza y comprobación ───────────────────────────────────────────

drop function _nuevo_id(text, text);
drop table _foranea_guardada;
drop table _columna_foranea;
drop table _mapa_id;
drop table _tabla_a_convertir;

-- No debe quedar ningún id ni ninguna foránea en texto.
do $$
declare
    sobrante text;
begin
    select string_agg(format('%s.%s', c.table_name, c.column_name), ', ')
      into sobrante
      from information_schema.columns c
     where c.table_schema = 'public'
       and c.table_name <> 'flyway_schema_history'
       and c.data_type = 'text'
       and (c.column_name = 'id' or c.column_name like '%Id');

    if sobrante is not null then
        raise exception 'Quedaron columnas de id en texto: %', sobrante;
    end if;
end $$;
