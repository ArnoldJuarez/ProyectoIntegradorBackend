-- El local en dos plantas.
--
-- Hasta ahora el plano era una sola planta con tres cajas (pista, VIP y barra)
-- sobre 32 × 22 m. Ssiomama es más grande que eso y tiene dos pisos con
-- ambientes distintos, y el plano tiene que poder decirlo: en qué planta está
-- cada zona y cada salida. Sin eso la vista 3D no puede subir la terraza al
-- segundo piso, y la evacuación mandaría a la gente del balcón a una puerta de
-- la planta baja como si no hubiera escaleras de por medio.

-- 0 = planta baja, 1 = planta alta. Un entero y no un booleano porque nada
-- impide que mañana haya un sótano (-1) o una azotea (2).
alter table "Zona" add column piso integer not null default 0;
alter table "Puerta" add column piso integer not null default 0;

-- ── La distribución de dos plantas ──────────────────────────────────
--
-- Solo se aplica si el local sigue como lo dejó el seed (32 × 22 m con sus
-- tres zonas). Si alguien ya lo ha redibujado a mano desde el panel, no se le
-- pisa: la migración añade las columnas y nada más.
--
-- Planta baja (suelo a 0 m)                Planta alta (suelo a 4,5 m)
--   Escenario y pista: doble altura (9 m)    Lounge VIP y su barra
--   Boxes de pista, barra principal          Mirador sobre la barra
--   La Cueva (sala urbana)                   Balcón sobre la pista
--   Coctelería, vestíbulo, baños, escalera   Terraza al aire libre
--
-- El aforo de la sala no cambia (500): se reparte entre las zonas, que es lo
-- que usa la evacuación para saber cuánta gente sale de cada sitio.

do $$
declare
    sala text;
begin
    select id into sala from "Sala"
     where "planoAncho" = 32 and "planoFondo" = 22
       and (select count(*) from "Zona" z where z."salaId" = "Sala".id) = 3
     limit 1;

    if sala is null then
        return;
    end if;

    update "Sala" set "planoAncho" = 44, "planoFondo" = 30 where id = sala;

    -- Las tres zonas de siempre, en su sitio nuevo. Mantienen su id: los
    -- reservados y su histórico apuntan a ellas.
    update "Zona" set nombre = 'Pista principal', x = 4, y = 6, ancho = 18, fondo = 15, alto = 9,
                      aforo = 160, orden = 2, piso = 0, tono = -1
     where "salaId" = sala and tipo = 'PISTA';
    update "Zona" set nombre = 'Barra principal', x = 22, y = 5, ancho = 9, fondo = 14, alto = 4.2,
                      aforo = 50, orden = 4, piso = 0, tono = -1
     where "salaId" = sala and tipo = 'BARRA';
    update "Zona" set nombre = 'Lounge VIP', x = 22, y = 1, ancho = 13, fondo = 10, alto = 3.5,
                      aforo = 45, orden = 11, piso = 1, tono = -1
     where "salaId" = sala and tipo = 'VIP';

    insert into "Zona" (id, "salaId", nombre, tipo, aforo, orden, x, y, ancho, fondo, alto, tono, piso) values
        ('zona-escenario',     sala, 'Escenario',              'OTRA',  0,   1,  4,   1,  18,  5,   9,   300, 0),
        ('zona-boxes-pista',   sala, 'Boxes de pista',         'VIP',   20,  3,  0.6, 7,  3.4, 13,  4.2, -1,  0),
        ('zona-cueva',         sala, 'La Cueva · sala urbana', 'PISTA', 70,  5,  31,  1,  12,  17,  4.2, 12,  0),
        ('zona-cocteleria',    sala, 'Coctelería',             'BARRA', 25,  6,  1,   22, 11,  7.5, 4.2, 32,  0),
        ('zona-vestibulo',     sala, 'Vestíbulo y guardarropa','OTRA',  0,   7,  12,  22, 12,  7.5, 4.2, 205, 0),
        ('zona-banos',         sala, 'Baños',                  'OTRA',  0,   8,  31,  19, 12,  6,   3.2, 220, 0),
        ('zona-escalera',      sala, 'Escalera',               'OTRA',  0,   9,  24,  21, 6,   8,   9,   0,   0),
        ('zona-barra-vip',     sala, 'Barra VIP',              'BARRA', 15,  12, 35,  1,  8,   10,  3.5, 45,  1),
        ('zona-mirador',       sala, 'Mirador',                'OTRA',  20,  13, 22,  11, 9,   10,  3.5, 195, 1),
        ('zona-balcon',        sala, 'Balcón sobre la pista',  'OTRA',  35,  14, 1,   21, 23,  8,   3.5, 280, 1),
        ('zona-terraza',       sala, 'Terraza',                'OTRA',  60,  15, 31,  11, 12,  18,  3,   150, 1)
    on conflict (id) do nothing;

    -- Los reservados de la pista pasan a su zona propia, junto a la pared.
    update "Box" set "zonaId" = 'zona-boxes-pista'
     where "salaId" = sala
       and "zonaId" = (select id from "Zona" where "salaId" = sala and tipo = 'PISTA' and id <> 'zona-cueva');

    -- Las puertas de siempre, en la fachada nueva.
    update "Puerta" set x = 18, y = 30, ancho = 2.4, piso = 0
     where "salaId" = sala and not "esSalidaEmergencia";
    update "Puerta" set x = 0, y = 25.5, piso = 0
     where "salaId" = sala and "esSalidaEmergencia" and nombre ilike '%oeste%';
    update "Puerta" set x = 37, y = 0, piso = 0
     where "salaId" = sala and "esSalidaEmergencia" and nombre ilike '%norte%';

    -- La planta alta necesita sus propias salidas: escaleras de emergencia
    -- por fuera del edificio, una a cada lado.
    insert into "Puerta" (id, "salaId", nombre, "esSalidaEmergencia", "caudalPorMinuto", activo, ancho, x, y, piso) values
        ('puerta-emergencia-terraza', sala, 'Escalera de emergencia (terraza)', true,  70, true, 1.4, 44, 24, 1),
        ('puerta-emergencia-balcon',  sala, 'Escalera de emergencia (balcón)',  true,  70, true, 1.4, 0,  25, 1),
        ('puerta-servicio',           sala, 'Puerta de servicio',               false, 0,  true, 1.2, 44, 8,  0)
    on conflict (id) do nothing;
end $$;
