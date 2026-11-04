-- El expediente de cada noche, las barras y lo que se le paga a cada promotor.
--
-- Tres huecos que impedían contestar «¿cómo fue la noche?» de una vez:
--
--   1. Un turno de caja no decía en qué barra se trabajó: se sabía cuánto
--      cobró cada persona, pero no cuánto vendió cada barra.
--   2. La comisión de un promotor se calculaba, pero no quedaba rastro de lo
--      que ya se le había pagado: nada impedía pagarle dos veces lo mismo.
--   3. Lo que pasó en la noche y no es una venta —una pelea, un corte de luz,
--      una queja— no tenía dónde apuntarse, y es justo lo que explica unos
--      números raros cuando se miran un mes después.

-- ── 1. La barra de cada turno ───────────────────────────────────────

-- Una barra es un puesto marcado como «esBarra». Nulo solo en turnos viejos
-- que no se puedan atribuir.
alter table "SesionCaja" add column "puestoId" text
    references "Puesto"(id) on update cascade on delete set null;

-- Los turnos que ya existían: la barra de quien lo abrió, si era de barra…
update "SesionCaja" s
   set "puestoId" = u."puestoId"
  from "Usuario" u
 where u.id = s."usuarioId"
   and u."puestoId" in (select id from "Puesto" where "esBarra");

-- …y si no, la única barra que había entonces.
update "SesionCaja"
   set "puestoId" = (select id from "Puesto" where "esBarra" order by nombre limit 1)
 where "puestoId" is null;

create index "SesionCaja_puestoId_idx" on "SesionCaja"("puestoId");

-- ── 2. Lo que se le paga a cada promotor ────────────────────────────

-- Pago fijo por cada persona de su lista que entra de verdad. Es como se paga a
-- muchos promotores en la ciudad: por cabeza, además o en vez del porcentaje.
alter table "Usuario" add column "bonoInvitadoCentimos" integer not null default 0;

-- Una liquidación es un pago hecho: cuánto, por qué y cómo. Las cifras quedan
-- copiadas y no se recalculan, igual que un arqueo: si mañana cambia el
-- porcentaje del promotor, lo que se le pagó entonces no cambia.
create table "LiquidacionPromotor" (
    id                     text primary key,
    "promotorId"           text not null references "Usuario"(id) on update cascade on delete restrict,
    -- Cubre las noches que empezaron hasta este instante.
    hasta                  timestamp(3) without time zone not null,
    pedidos                integer not null,
    "entradas"             integer not null,
    "ventasCentimos"       integer not null,
    "comisionPct"          integer not null,
    "comisionCentimos"     integer not null,
    "invitadosEntraron"    integer not null,
    "bonoInvitadoCentimos" integer not null,
    "bonoCentimos"         integer not null,
    -- Lo que se suma o se resta a mano (un adelanto, una penalización). Con nota.
    "ajusteCentimos"       integer not null default 0,
    "totalCentimos"        integer not null,
    "metodoPago"           "MetodoPago" not null,
    referencia             text,
    nota                   text,
    "registradaPorId"      text references "Usuario"(id) on update cascade on delete set null,
    "pagadaEn"             timestamp(3) without time zone not null
);

create index "LiquidacionPromotor_promotorId_pagadaEn_idx"
    on "LiquidacionPromotor"("promotorId", "pagadaEn");

-- Lo que entra en cada liquidación queda marcado: lo marcado no vuelve a
-- salir como pendiente. Es lo que impide pagar dos veces el mismo pedido.
alter table "Pedido" add column "liquidacionId" text
    references "LiquidacionPromotor"(id) on update cascade on delete set null;
alter table "ListaInvitados" add column "liquidacionId" text
    references "LiquidacionPromotor"(id) on update cascade on delete set null;

-- Las ventas de cada promotor se consultan en cada ficha: sin índice, cada una
-- recorre los seis mil pedidos.
create index "Pedido_promotorId_estado_idx" on "Pedido"("promotorId", estado)
    where "promotorId" is not null;

-- ── 3. Lo que pasó en la noche ──────────────────────────────────────

-- Lo que la jefatura de sala escribe al cerrar: cómo fue, en sus palabras.
alter table "Evento" add column "notasCierre" text;

create table "IncidenciaNoche" (
    id                text primary key,
    "eventoId"        text not null references "Evento"(id) on update cascade on delete cascade,
    tipo              text not null
        check (tipo in ('SEGURIDAD', 'SALUD', 'OPERACION', 'CLIENTE', 'PERSONAL', 'OTRA')),
    descripcion       text not null,
    "registradaPorId" text references "Usuario"(id) on update cascade on delete set null,
    "ocurrioEn"       timestamp(3) without time zone not null,
    "creadaEn"        timestamp(3) without time zone not null
);

create index "IncidenciaNoche_eventoId_ocurrioEn_idx" on "IncidenciaNoche"("eventoId", "ocurrioEn");
