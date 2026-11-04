-- El video de una noche y el agente que propone noches nuevas.
--
-- Primera migración propia del proyecto Java: V1 es el esquema heredado y no se
-- toca nunca (Flyway guarda su checksum).

-- Un clip para la ficha pública: o un archivo subido desde el panel
-- (/api/publico/videos/...), o la dirección de un MP4 o de YouTube pegada a mano.
-- Nulo cuando la noche solo tiene flyer, que es lo normal.
alter table "Evento" add column "video" text;

-- El programador estudia el histórico y propone noches completas. Como todos
-- los agentes, no crea nada por su cuenta: deja una propuesta que una persona
-- aprueba (y puede retocar antes) desde el panel.
--
-- ADD VALUE puede ir dentro de la transacción de Flyway desde PostgreSQL 12,
-- con una condición: el valor nuevo no se puede usar en esta misma
-- transacción. Aquí no se usa.
alter type "ClaveAgente" add value if not exists 'programmer';
alter type "TipoPropuesta" add value if not exists 'EVENTO';
