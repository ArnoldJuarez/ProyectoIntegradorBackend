-- Lo que hace falta para un plan de evacuación además de las puertas.
--
-- Hasta ahora el plano sabía de zonas y salidas. Un plan de evacuación de
-- verdad (el que pide la inspección técnica de seguridad, ITSE) dice además
-- dónde están los extintores, el botiquín, las luces de emergencia, los
-- pulsadores de alarma, las zonas seguras en caso de sismo —Arequipa es zona
-- sísmica— y el punto de reunión fuera del local, que es adonde tiene que ir la
-- gente una vez fuera. Todo eso son puntos en el plano con un tipo.
--
-- El punto de reunión está FUERA del edificio: su «y» puede pasar del fondo de
-- la sala (la acera o la calle de enfrente). Por eso no se limita al plano.

create table "ElementoSeguridad" (
    id         text primary key,
    "salaId"   text not null references "Sala"(id) on update cascade on delete cascade,
    tipo       text not null
        check (tipo in ('EXTINTOR', 'BOTIQUIN', 'LUZ_EMERGENCIA', 'ALARMA', 'ZONA_SEGURA', 'PUNTO_REUNION')),
    nombre     text not null,
    piso       integer not null default 0,
    x          double precision not null,
    y          double precision not null
);

create index "ElementoSeguridad_salaId_idx" on "ElementoSeguridad"("salaId");

-- Los de la sala principal, si tiene la distribución de dos plantas (V4).
-- Repartidos con los criterios habituales: extintor junto a cada salida, a
-- cada barra y a la cabina del DJ; luz de emergencia en cada salida y en la
-- escalera; zonas seguras junto a muros y lejos del vidrio; el punto de reunión
-- en la acera de enfrente, a distancia de la fachada.
insert into "ElementoSeguridad" (id, "salaId", tipo, nombre, piso, x, y)
select v.id, s.id, v.tipo, v.nombre, v.piso, v.x, v.y
  from "Sala" s
 cross join (values
    ('seg-reunion-1',  'PUNTO_REUNION',  'Punto de reunión (acera de enfrente)', 0, 30.0, 37.0),
    ('seg-reunion-2',  'PUNTO_REUNION',  'Punto de reunión (esquina oeste)',     0,  4.0, 36.0),

    ('seg-segura-1',   'ZONA_SEGURA',    'Zona segura · pista (muro este)',      0, 21.4, 19.6),
    ('seg-segura-2',   'ZONA_SEGURA',    'Zona segura · La Cueva',               0, 31.8,  2.0),
    ('seg-segura-3',   'ZONA_SEGURA',    'Zona segura · vestíbulo',              0, 12.8, 28.6),
    ('seg-segura-4',   'ZONA_SEGURA',    'Zona segura · coctelería',             0,  1.6, 28.6),
    ('seg-segura-5',   'ZONA_SEGURA',    'Zona segura · escenario',              0,  4.6,  1.6),
    ('seg-segura-6',   'ZONA_SEGURA',    'Zona segura · lounge VIP',             1, 34.4,  1.6),
    ('seg-segura-7',   'ZONA_SEGURA',    'Zona segura · terraza',                1, 31.6, 28.4),
    ('seg-segura-8',   'ZONA_SEGURA',    'Zona segura · balcón',                 1,  1.6, 28.4),

    ('seg-ext-1',      'EXTINTOR',       'Extintor PQS · puerta principal',      0, 16.4, 29.4),
    ('seg-ext-2',      'EXTINTOR',       'Extintor PQS · salida oeste',          0,  0.6, 24.2),
    ('seg-ext-3',      'EXTINTOR',       'Extintor PQS · salida norte',          0, 35.6,  0.6),
    ('seg-ext-4',      'EXTINTOR',       'Extintor PQS · barra principal',       0, 22.4,  5.6),
    ('seg-ext-5',      'EXTINTOR',       'Extintor CO₂ · cabina del DJ',         0,  4.6,  5.4),
    ('seg-ext-6',      'EXTINTOR',       'Extintor PQS · La Cueva',              0, 42.4, 17.4),
    ('seg-ext-7',      'EXTINTOR',       'Extintor PQS · baños',                 0, 31.6, 19.6),
    ('seg-ext-8',      'EXTINTOR',       'Extintor PQS · coctelería',            0, 11.4, 22.6),
    ('seg-ext-9',      'EXTINTOR',       'Extintor PQS · lounge VIP',            1, 22.6, 10.4),
    ('seg-ext-10',     'EXTINTOR',       'Extintor CO₂ · barra VIP',             1, 35.6,  1.6),
    ('seg-ext-11',     'EXTINTOR',       'Extintor PQS · terraza',               1, 43.4, 22.6),
    ('seg-ext-12',     'EXTINTOR',       'Extintor PQS · balcón',                1,  0.6, 23.6),
    ('seg-ext-13',     'EXTINTOR',       'Extintor PQS · mirador',               1, 30.4, 20.4),

    ('seg-botiquin-1', 'BOTIQUIN',       'Botiquín y desfibrilador (DEA)',       0, 23.4, 25.0),
    ('seg-botiquin-2', 'BOTIQUIN',       'Botiquín · planta alta',               1, 34.4, 10.4),

    ('seg-luz-1',      'LUZ_EMERGENCIA', 'Luz de emergencia · puerta principal', 0, 18.0, 29.6),
    ('seg-luz-2',      'LUZ_EMERGENCIA', 'Luz de emergencia · salida oeste',     0,  0.4, 25.5),
    ('seg-luz-3',      'LUZ_EMERGENCIA', 'Luz de emergencia · salida norte',     0, 37.0,  0.4),
    ('seg-luz-4',      'LUZ_EMERGENCIA', 'Luz de emergencia · pie de escalera',  0, 27.0, 28.6),
    ('seg-luz-5',      'LUZ_EMERGENCIA', 'Luz de emergencia · pista',            0, 13.0,  6.4),
    ('seg-luz-6',      'LUZ_EMERGENCIA', 'Luz de emergencia · La Cueva',         0, 37.0, 17.6),
    ('seg-luz-7',      'LUZ_EMERGENCIA', 'Luz de emergencia · escalera terraza', 1, 43.6, 24.0),
    ('seg-luz-8',      'LUZ_EMERGENCIA', 'Luz de emergencia · escalera balcón',  1,  0.4, 25.0),
    ('seg-luz-9',      'LUZ_EMERGENCIA', 'Luz de emergencia · desembarco',       1, 27.0, 20.4),

    ('seg-alarma-1',   'ALARMA',         'Pulsador de alarma · vestíbulo',       0, 23.6, 28.8),
    ('seg-alarma-2',   'ALARMA',         'Pulsador de alarma · La Cueva',        0, 38.4,  0.6),
    ('seg-alarma-3',   'ALARMA',         'Pulsador de alarma · terraza',         1, 43.4, 25.4)
 ) as v(id, tipo, nombre, piso, x, y)
 where s."planoAncho" = 44 and s."planoFondo" = 30
on conflict (id) do nothing;
