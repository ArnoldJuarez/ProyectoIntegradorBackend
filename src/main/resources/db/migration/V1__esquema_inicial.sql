-- ─────────────────────────────────────────────────────────────────────────
--  Esquema inicial (nombres en espanol).
--
--  Volcado de la base `teca` con `pg_dump --schema-only`. Sustituye al
--  volcado anterior, que describia el esquema en ingles de `discoteca`.
--
--  38 tablas, 21 tipos enum. Respecto al esquema anterior:
--    - Employee y Promoter se fusionaron en Usuario
--    - StockItem se colapso en la columna Producto.stock
--    - CustomerSegment y TimeClock desaparecieron
--    - Los valores de los enums tambien pasaron a espanol
--
--  Sobre una base que ya tiene estas tablas, Flyway no ejecuta este archivo:
--  `baseline-on-migrate` la marca como version 1 ya aplicada. Sobre una base
--  vacia si se ejecuta, y deja el esquema completo listo.
--
--  Se le quitaron las lineas `\restrict` y `\unrestrict` que anade pg_dump 18:
--  son metacomandos de psql, no SQL, y Flyway no los entiende.
-- ─────────────────────────────────────────────────────────────────────────

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: CanalCampania; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."CanalCampania" AS ENUM (
    'CORREO',
    'SMS',
    'WHATSAPP',
    'PUSH'
);


--
-- Name: CanalChat; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."CanalChat" AS ENUM (
    'WEB',
    'WHATSAPP',
    'INSTAGRAM'
);


--
-- Name: CanalInteraccion; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."CanalInteraccion" AS ENUM (
    'WEB',
    'WHATSAPP',
    'CORREO',
    'TELEFONO',
    'INSTAGRAM',
    'EN_PERSONA'
);


--
-- Name: ClaveAgente; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ClaveAgente" AS ENUM (
    'marketing',
    'crm-segmenter',
    'inventory',
    'pricing',
    'reporter',
    'concierge',
    'inventory-ml'
);


--
-- Name: DisparadorAgente; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."DisparadorAgente" AS ENUM (
    'manual',
    'scheduled',
    'chat',
    'benchmark'
);


--
-- Name: EstadoCampania; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoCampania" AS ENUM (
    'BORRADOR',
    'PROGRAMADO',
    'ENVIADO',
    'CANCELADO'
);


--
-- Name: EstadoEjecucion; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoEjecucion" AS ENUM (
    'EN_COLA',
    'EJECUTANDO',
    'COMPLETADA',
    'FALLIDA'
);


--
-- Name: EstadoEntrada; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoEntrada" AS ENUM (
    'VALIDA',
    'USADA',
    'ANULADA',
    'DEVUELTO'
);


--
-- Name: EstadoEvento; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoEvento" AS ENUM (
    'BORRADOR',
    'PUBLICADO',
    'AGOTADO',
    'CANCELADO',
    'CERRADO'
);


--
-- Name: EstadoOrdenCompra; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoOrdenCompra" AS ENUM (
    'BORRADOR',
    'ENVIADO',
    'RECIBIDO',
    'CANCELADO'
);


--
-- Name: EstadoPedido; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoPedido" AS ENUM (
    'PENDIENTE',
    'PAGADO',
    'CANCELADO',
    'DEVUELTO'
);


--
-- Name: EstadoPropuesta; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoPropuesta" AS ENUM (
    'PENDIENTE',
    'APROBADA',
    'RECHAZADA'
);


--
-- Name: EstadoReserva; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EstadoReserva" AS ENUM (
    'SOLICITADA',
    'CONFIRMADA',
    'SENTADA',
    'NO_VINO',
    'CANCELADO'
);


--
-- Name: MetodoPago; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."MetodoPago" AS ENUM (
    'EFECTIVO',
    'TARJETA',
    'YAPE',
    'PLIN',
    'TRANSFERENCIA'
);


--
-- Name: Rol; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."Rol" AS ENUM (
    'DIRECCION',
    'GERENCIA',
    'PROMOTOR',
    'PUERTA',
    'BARRA',
    'PERSONAL'
);


--
-- Name: RolChat; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."RolChat" AS ENUM (
    'user',
    'assistant',
    'system'
);


--
-- Name: TipoInteraccion; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."TipoInteraccion" AS ENUM (
    'COMPRA',
    'VISITA',
    'RESERVA',
    'CAMPANIA',
    'CHAT',
    'QUEJA',
    'NOTA'
);


--
-- Name: TipoMovimientoStock; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."TipoMovimientoStock" AS ENUM (
    'COMPRA',
    'VENTA',
    'MERMA',
    'AJUSTE',
    'TRANSFERENCIA'
);


--
-- Name: TipoPropuesta; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."TipoPropuesta" AS ENUM (
    'TEXTO_CAMPANIA',
    'CAMPANIA',
    'ORDEN_COMPRA',
    'CAMBIO_PRECIO',
    'INFORME'
);


--
-- Name: TipoZona; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."TipoZona" AS ENUM (
    'PISTA',
    'VIP',
    'BARRA',
    'OTRA'
);


--
-- Name: UnidadProducto; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."UnidadProducto" AS ENUM (
    'UNIDAD',
    'BOTELLA',
    'CAJA',
    'LITRO'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: Acceso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Acceso" (
    id text CONSTRAINT "CheckIn_id_not_null" NOT NULL,
    "entradaId" text CONSTRAINT "CheckIn_ticketId_not_null" NOT NULL,
    "eventoId" text CONSTRAINT "CheckIn_eventId_not_null" NOT NULL,
    "escaneadoPorId" text CONSTRAINT "CheckIn_scannedById_not_null" NOT NULL,
    "puertaId" text,
    "escaneadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "CheckIn_scannedAt_not_null" NOT NULL
);


--
-- Name: Artista; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Artista" (
    id text CONSTRAINT "Artist_id_not_null" NOT NULL,
    nombre text CONSTRAINT "Artist_name_not_null" NOT NULL,
    genero text,
    "urlImagen" text,
    instagram text,
    bio text
);


--
-- Name: Box; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Box" (
    id text CONSTRAINT "VipTable_id_not_null" NOT NULL,
    "salaId" text CONSTRAINT "VipTable_venueId_not_null" NOT NULL,
    "zonaId" text,
    nombre text CONSTRAINT "VipTable_name_not_null" NOT NULL,
    plazas integer CONSTRAINT "VipTable_seats_not_null" NOT NULL,
    "consumoMinimoCentimos" integer DEFAULT 0 CONSTRAINT "VipTable_minSpendCents_not_null" NOT NULL,
    activo boolean DEFAULT true CONSTRAINT "VipTable_active_not_null" NOT NULL
);


--
-- Name: Campania; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Campania" (
    id text CONSTRAINT "Campaign_id_not_null" NOT NULL,
    nombre text CONSTRAINT "Campaign_name_not_null" NOT NULL,
    canal public."CanalCampania" CONSTRAINT "Campaign_channel_not_null" NOT NULL,
    "segmentoId" text CONSTRAINT "Campaign_segmentId_not_null" NOT NULL,
    asunto text,
    cuerpo text CONSTRAINT "Campaign_body_not_null" NOT NULL,
    estado public."EstadoCampania" DEFAULT 'BORRADOR'::public."EstadoCampania" CONSTRAINT "Campaign_status_not_null" NOT NULL,
    "programadaEn" timestamp(3) without time zone,
    "enviadaEn" timestamp(3) without time zone,
    enviados integer DEFAULT 0 CONSTRAINT "Campaign_sentCount_not_null" NOT NULL,
    aperturas integer DEFAULT 0 CONSTRAINT "Campaign_openCount_not_null" NOT NULL,
    clics integer DEFAULT 0 CONSTRAINT "Campaign_clickCount_not_null" NOT NULL,
    "ejecucionId" text,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "Campaign_createdAt_not_null" NOT NULL
);


--
-- Name: CategoriaGasto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."CategoriaGasto" (
    id text CONSTRAINT "ExpenseCategory_id_not_null" NOT NULL,
    nombre text CONSTRAINT "ExpenseCategory_name_not_null" NOT NULL,
    activo boolean DEFAULT true CONSTRAINT "ExpenseCategory_active_not_null" NOT NULL
);


--
-- Name: CategoriaProducto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."CategoriaProducto" (
    id text CONSTRAINT "ProductCategory_id_not_null" NOT NULL,
    nombre text CONSTRAINT "ProductCategory_name_not_null" NOT NULL
);


--
-- Name: Cliente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Cliente" (
    id text CONSTRAINT "Customer_id_not_null" NOT NULL,
    nombre text CONSTRAINT "Customer_firstName_not_null" NOT NULL,
    apellido text,
    correo text CONSTRAINT "Customer_email_not_null" NOT NULL,
    telefono text,
    "fechaNacimiento" date,
    distrito text,
    "consienteMarketing" boolean DEFAULT false CONSTRAINT "Customer_marketingConsent_not_null" NOT NULL,
    "consintioEn" timestamp(3) without time zone,
    visitas integer DEFAULT 0 CONSTRAINT "Customer_visitCount_not_null" NOT NULL,
    "gastoTotalCentimos" integer DEFAULT 0 CONSTRAINT "Customer_totalSpentCents_not_null" NOT NULL,
    "ultimaVisitaEn" timestamp(3) without time zone,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "Customer_createdAt_not_null" NOT NULL
);


--
-- Name: EjecucionAgente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."EjecucionAgente" (
    id text CONSTRAINT "AgentRun_id_not_null" NOT NULL,
    "claveAgente" public."ClaveAgente" CONSTRAINT "AgentRun_agentKey_not_null" NOT NULL,
    disparador public."DisparadorAgente" CONSTRAINT "AgentRun_trigger_not_null" NOT NULL,
    "lanzadoPorId" text,
    entrada jsonb,
    salida jsonb,
    estado public."EstadoEjecucion" DEFAULT 'EN_COLA'::public."EstadoEjecucion" CONSTRAINT "AgentRun_status_not_null" NOT NULL,
    modelo text,
    "tokensEntrada" integer DEFAULT 0 CONSTRAINT "AgentRun_inputTokens_not_null" NOT NULL,
    "tokensSalida" integer DEFAULT 0 CONSTRAINT "AgentRun_outputTokens_not_null" NOT NULL,
    "costeCentimos" integer DEFAULT 0 CONSTRAINT "AgentRun_costCents_not_null" NOT NULL,
    error text,
    "iniciadaEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "AgentRun_startedAt_not_null" NOT NULL,
    "terminadaEn" timestamp(3) without time zone,
    intentos integer DEFAULT 1 CONSTRAINT "AgentRun_attempts_not_null" NOT NULL
);


--
-- Name: Entrada; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Entrada" (
    id text CONSTRAINT "Ticket_id_not_null" NOT NULL,
    codigo text CONSTRAINT "Ticket_code_not_null" NOT NULL,
    "pedidoId" text CONSTRAINT "Ticket_orderId_not_null" NOT NULL,
    "tipoEntradaId" text CONSTRAINT "Ticket_ticketTypeId_not_null" NOT NULL,
    "eventoId" text CONSTRAINT "Ticket_eventId_not_null" NOT NULL,
    "nombreTitular" text,
    "dniTitular" text,
    estado public."EstadoEntrada" DEFAULT 'VALIDA'::public."EstadoEntrada" CONSTRAINT "Ticket_status_not_null" NOT NULL,
    "emitidaEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "Ticket_issuedAt_not_null" NOT NULL
);


--
-- Name: Evento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Evento" (
    id text CONSTRAINT "Event_id_not_null" NOT NULL,
    slug text CONSTRAINT "Event_slug_not_null" NOT NULL,
    titulo text CONSTRAINT "Event_title_not_null" NOT NULL,
    descripcion text,
    "imagenPortada" text,
    "salaId" text CONSTRAINT "Event_venueId_not_null" NOT NULL,
    "aperturaPuertas" timestamp(3) without time zone CONSTRAINT "Event_doorsAt_not_null" NOT NULL,
    "empiezaEn" timestamp(3) without time zone CONSTRAINT "Event_startsAt_not_null" NOT NULL,
    "terminaEn" timestamp(3) without time zone CONSTRAINT "Event_endsAt_not_null" NOT NULL,
    "edadMinima" integer DEFAULT 18 CONSTRAINT "Event_minAge_not_null" NOT NULL,
    vestimenta text,
    estado public."EstadoEvento" DEFAULT 'BORRADOR'::public."EstadoEvento" CONSTRAINT "Event_status_not_null" NOT NULL,
    "publicadoEn" timestamp(3) without time zone,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "Event_createdAt_not_null" NOT NULL
);


--
-- Name: EventoArtista; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."EventoArtista" (
    "eventoId" text CONSTRAINT "EventArtist_eventId_not_null" NOT NULL,
    "artistaId" text CONSTRAINT "EventArtist_artistId_not_null" NOT NULL,
    "inicioSet" timestamp(3) without time zone,
    "finSet" timestamp(3) without time zone,
    "cabezaCartel" boolean DEFAULT false CONSTRAINT "EventArtist_headliner_not_null" NOT NULL
);


--
-- Name: Gasto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Gasto" (
    id text CONSTRAINT "Expense_id_not_null" NOT NULL,
    "eventoId" text,
    "categoriaId" text CONSTRAINT "Expense_categoryId_not_null" NOT NULL,
    "importeCentimos" integer CONSTRAINT "Expense_amountCents_not_null" NOT NULL,
    descripcion text,
    "usuarioId" text,
    "fechaGasto" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "Expense_incurredAt_not_null" NOT NULL
);


--
-- Name: Interaccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Interaccion" (
    id text CONSTRAINT "Interaction_id_not_null" NOT NULL,
    "clienteId" text CONSTRAINT "Interaction_customerId_not_null" NOT NULL,
    tipo public."TipoInteraccion" CONSTRAINT "Interaction_type_not_null" NOT NULL,
    canal public."CanalInteraccion",
    resumen text CONSTRAINT "Interaction_summary_not_null" NOT NULL,
    metadatos jsonb,
    "ocurrioEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "Interaction_occurredAt_not_null" NOT NULL
);


--
-- Name: Invitado; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Invitado" (
    id text CONSTRAINT "GuestListEntry_id_not_null" NOT NULL,
    "listaInvitadosId" text CONSTRAINT "GuestListEntry_guestListId_not_null" NOT NULL,
    "nombreCompleto" text CONSTRAINT "GuestListEntry_fullName_not_null" NOT NULL,
    personas integer DEFAULT 1 CONSTRAINT "GuestListEntry_guests_not_null" NOT NULL,
    entro boolean DEFAULT false CONSTRAINT "GuestListEntry_checkedIn_not_null" NOT NULL,
    "entroEn" timestamp(3) without time zone
);


--
-- Name: LineaOrdenCompra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."LineaOrdenCompra" (
    id text CONSTRAINT "PurchaseOrderItem_id_not_null" NOT NULL,
    "ordenCompraId" text CONSTRAINT "PurchaseOrderItem_purchaseOrderId_not_null" NOT NULL,
    "productoId" text CONSTRAINT "PurchaseOrderItem_productId_not_null" NOT NULL,
    cantidad integer CONSTRAINT "PurchaseOrderItem_quantity_not_null" NOT NULL,
    "costeUnitarioCentimos" integer CONSTRAINT "PurchaseOrderItem_unitCostCents_not_null" NOT NULL
);


--
-- Name: LineaPedido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."LineaPedido" (
    id text CONSTRAINT "OrderItem_id_not_null" NOT NULL,
    "pedidoId" text CONSTRAINT "OrderItem_orderId_not_null" NOT NULL,
    "tipoEntradaId" text CONSTRAINT "OrderItem_ticketTypeId_not_null" NOT NULL,
    cantidad integer CONSTRAINT "OrderItem_quantity_not_null" NOT NULL,
    "precioUnitarioCentimos" integer CONSTRAINT "OrderItem_unitPriceCents_not_null" NOT NULL
);


--
-- Name: LineaVentaBarra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."LineaVentaBarra" (
    id text CONSTRAINT "BarSaleItem_id_not_null" NOT NULL,
    "ventaBarraId" text CONSTRAINT "BarSaleItem_barSaleId_not_null" NOT NULL,
    "productoId" text CONSTRAINT "BarSaleItem_productId_not_null" NOT NULL,
    cantidad integer CONSTRAINT "BarSaleItem_quantity_not_null" NOT NULL,
    "precioUnitarioCentimos" integer CONSTRAINT "BarSaleItem_unitPriceCents_not_null" NOT NULL
);


--
-- Name: ListaInvitados; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."ListaInvitados" (
    id text CONSTRAINT "GuestList_id_not_null" NOT NULL,
    "eventoId" text CONSTRAINT "GuestList_eventId_not_null" NOT NULL,
    "promotorId" text CONSTRAINT "GuestList_promoterId_not_null" NOT NULL,
    nombre text CONSTRAINT "GuestList_name_not_null" NOT NULL,
    "maxInvitados" integer DEFAULT 50 CONSTRAINT "GuestList_maxGuests_not_null" NOT NULL
);


--
-- Name: MensajeChat; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."MensajeChat" (
    id text CONSTRAINT "ChatMessage_id_not_null" NOT NULL,
    "sesionId" text CONSTRAINT "ChatMessage_sessionId_not_null" NOT NULL,
    rol public."RolChat" CONSTRAINT "ChatMessage_role_not_null" NOT NULL,
    contenido text CONSTRAINT "ChatMessage_content_not_null" NOT NULL,
    "ejecucionId" text,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "ChatMessage_createdAt_not_null" NOT NULL
);


--
-- Name: MovimientoFidelidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."MovimientoFidelidad" (
    id text CONSTRAINT "LoyaltyLedger_id_not_null" NOT NULL,
    "clienteId" text CONSTRAINT "LoyaltyLedger_customerId_not_null" NOT NULL,
    puntos integer CONSTRAINT "LoyaltyLedger_points_not_null" NOT NULL,
    motivo text CONSTRAINT "LoyaltyLedger_reason_not_null" NOT NULL,
    "pedidoId" text,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "LoyaltyLedger_createdAt_not_null" NOT NULL
);


--
-- Name: MovimientoStock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."MovimientoStock" (
    id text CONSTRAINT "StockMovement_id_not_null" NOT NULL,
    "productoId" text CONSTRAINT "StockMovement_productId_not_null" NOT NULL,
    "salaId" text CONSTRAINT "StockMovement_venueId_not_null" NOT NULL,
    tipo public."TipoMovimientoStock" CONSTRAINT "StockMovement_type_not_null" NOT NULL,
    cantidad integer CONSTRAINT "StockMovement_quantity_not_null" NOT NULL,
    "costeUnitarioCentimos" integer DEFAULT 0 CONSTRAINT "StockMovement_unitCostCents_not_null" NOT NULL,
    referencia text,
    "usuarioId" text,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "StockMovement_createdAt_not_null" NOT NULL
);


--
-- Name: OrdenCompra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."OrdenCompra" (
    id text CONSTRAINT "PurchaseOrder_id_not_null" NOT NULL,
    "proveedorId" text CONSTRAINT "PurchaseOrder_supplierId_not_null" NOT NULL,
    estado public."EstadoOrdenCompra" DEFAULT 'BORRADOR'::public."EstadoOrdenCompra" CONSTRAINT "PurchaseOrder_status_not_null" NOT NULL,
    "totalCentimos" integer DEFAULT 0 CONSTRAINT "PurchaseOrder_totalCents_not_null" NOT NULL,
    "esperadoEn" timestamp(3) without time zone,
    "recibidoEn" timestamp(3) without time zone,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "PurchaseOrder_createdAt_not_null" NOT NULL
);


--
-- Name: Pedido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Pedido" (
    id text CONSTRAINT "Order_id_not_null" NOT NULL,
    codigo text CONSTRAINT "Order_code_not_null" NOT NULL,
    "clienteId" text CONSTRAINT "Order_customerId_not_null" NOT NULL,
    "eventoId" text CONSTRAINT "Order_eventId_not_null" NOT NULL,
    "promotorId" text,
    "subtotalCentimos" integer CONSTRAINT "Order_subtotalCents_not_null" NOT NULL,
    "comisionCentimos" integer DEFAULT 0 CONSTRAINT "Order_feesCents_not_null" NOT NULL,
    "totalCentimos" integer CONSTRAINT "Order_totalCents_not_null" NOT NULL,
    estado public."EstadoPedido" DEFAULT 'PENDIENTE'::public."EstadoPedido" CONSTRAINT "Order_status_not_null" NOT NULL,
    "metodoPago" public."MetodoPago",
    "refPago" text,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "Order_createdAt_not_null" NOT NULL,
    "pagadoEn" timestamp(3) without time zone
);


--
-- Name: Producto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Producto" (
    id text CONSTRAINT "Product_id_not_null" NOT NULL,
    sku text CONSTRAINT "Product_sku_not_null" NOT NULL,
    nombre text CONSTRAINT "Product_name_not_null" NOT NULL,
    "categoriaId" text CONSTRAINT "Product_categoryId_not_null" NOT NULL,
    unidad public."UnidadProducto" DEFAULT 'UNIDAD'::public."UnidadProducto" CONSTRAINT "Product_unit_not_null" NOT NULL,
    "costeCentimos" integer DEFAULT 0 CONSTRAINT "Product_costCents_not_null" NOT NULL,
    "precioCentimos" integer DEFAULT 0 CONSTRAINT "Product_priceCents_not_null" NOT NULL,
    "alertaStock" integer DEFAULT 10 CONSTRAINT "Product_stockAlert_not_null" NOT NULL,
    activo boolean DEFAULT true CONSTRAINT "Product_active_not_null" NOT NULL,
    stock integer DEFAULT 0 CONSTRAINT "Product_stock_not_null" NOT NULL
);


--
-- Name: PropuestaAgente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."PropuestaAgente" (
    id text CONSTRAINT "AgentProposal_id_not_null" NOT NULL,
    "ejecucionId" text CONSTRAINT "AgentProposal_agentRunId_not_null" NOT NULL,
    tipo public."TipoPropuesta" CONSTRAINT "AgentProposal_kind_not_null" NOT NULL,
    datos jsonb CONSTRAINT "AgentProposal_payload_not_null" NOT NULL,
    estado public."EstadoPropuesta" DEFAULT 'PENDIENTE'::public."EstadoPropuesta" CONSTRAINT "AgentProposal_status_not_null" NOT NULL,
    "revisadaPorId" text,
    "revisadaEn" timestamp(3) without time zone,
    "aplicadaEn" timestamp(3) without time zone,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "AgentProposal_createdAt_not_null" NOT NULL
);


--
-- Name: Proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Proveedor" (
    id text CONSTRAINT "Supplier_id_not_null" NOT NULL,
    nombre text CONSTRAINT "Supplier_name_not_null" NOT NULL,
    contacto text,
    correo text,
    telefono text
);


--
-- Name: Puerta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Puerta" (
    id text CONSTRAINT "Gate_id_not_null" NOT NULL,
    "salaId" text CONSTRAINT "Gate_venueId_not_null" NOT NULL,
    nombre text CONSTRAINT "Gate_name_not_null" NOT NULL,
    "esSalidaEmergencia" boolean DEFAULT false CONSTRAINT "Gate_isEmergencyExit_not_null" NOT NULL,
    "caudalPorMinuto" integer DEFAULT 0 CONSTRAINT "Gate_flowPerMinute_not_null" NOT NULL,
    activo boolean DEFAULT true CONSTRAINT "Gate_active_not_null" NOT NULL,
    ancho double precision DEFAULT 1.2 CONSTRAINT "Gate_width_not_null" NOT NULL,
    x double precision DEFAULT 0 CONSTRAINT "Gate_x_not_null" NOT NULL,
    y double precision DEFAULT 0 CONSTRAINT "Gate_y_not_null" NOT NULL
);


--
-- Name: Puesto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Puesto" (
    id text CONSTRAINT "StaffPosition_id_not_null" NOT NULL,
    nombre text CONSTRAINT "StaffPosition_name_not_null" NOT NULL,
    "esBarra" boolean DEFAULT false CONSTRAINT "StaffPosition_isBar_not_null" NOT NULL,
    activo boolean DEFAULT true CONSTRAINT "StaffPosition_active_not_null" NOT NULL
);


--
-- Name: ReservaBox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."ReservaBox" (
    id text CONSTRAINT "TableReservation_id_not_null" NOT NULL,
    "eventoId" text CONSTRAINT "TableReservation_eventId_not_null" NOT NULL,
    "boxId" text CONSTRAINT "TableReservation_tableId_not_null" NOT NULL,
    "clienteId" text CONSTRAINT "TableReservation_customerId_not_null" NOT NULL,
    personas integer CONSTRAINT "TableReservation_guests_not_null" NOT NULL,
    "depositoCentimos" integer DEFAULT 0 CONSTRAINT "TableReservation_depositCents_not_null" NOT NULL,
    estado public."EstadoReserva" DEFAULT 'SOLICITADA'::public."EstadoReserva" CONSTRAINT "TableReservation_status_not_null" NOT NULL,
    notas text,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "TableReservation_createdAt_not_null" NOT NULL
);


--
-- Name: Sala; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Sala" (
    id text CONSTRAINT "Venue_id_not_null" NOT NULL,
    nombre text CONSTRAINT "Venue_name_not_null" NOT NULL,
    aforo integer CONSTRAINT "Venue_capacity_not_null" NOT NULL,
    descripcion text,
    "planoFondo" double precision DEFAULT 0 CONSTRAINT "Venue_planDepth_not_null" NOT NULL,
    "planoAncho" double precision DEFAULT 0 CONSTRAINT "Venue_planWidth_not_null" NOT NULL
);


--
-- Name: Segmento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Segmento" (
    id text CONSTRAINT "Segment_id_not_null" NOT NULL,
    nombre text CONSTRAINT "Segment_name_not_null" NOT NULL,
    descripcion text,
    "reglasJson" jsonb CONSTRAINT "Segment_rulesJson_not_null" NOT NULL,
    "esDinamico" boolean DEFAULT true CONSTRAINT "Segment_isDynamic_not_null" NOT NULL,
    "actualizadoEn" timestamp(3) without time zone CONSTRAINT "Segment_updatedAt_not_null" NOT NULL
);


--
-- Name: SesionCaja; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."SesionCaja" (
    id text CONSTRAINT "CashSession_id_not_null" NOT NULL,
    "salaId" text CONSTRAINT "CashSession_venueId_not_null" NOT NULL,
    "usuarioId" text CONSTRAINT "CashSession_employeeId_not_null" NOT NULL,
    "fondoCentimos" integer DEFAULT 0 CONSTRAINT "CashSession_openingCents_not_null" NOT NULL,
    "esperadoCentimos" integer DEFAULT 0 CONSTRAINT "CashSession_expectedCents_not_null" NOT NULL,
    "contadoCentimos" integer,
    "descuadreCentimos" integer,
    "abiertaEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "CashSession_openedAt_not_null" NOT NULL,
    "cerradaEn" timestamp(3) without time zone
);


--
-- Name: SesionChat; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."SesionChat" (
    id text CONSTRAINT "ChatSession_id_not_null" NOT NULL,
    "clienteId" text,
    canal public."CanalChat" DEFAULT 'WEB'::public."CanalChat" CONSTRAINT "ChatSession_channel_not_null" NOT NULL,
    "iniciadaEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "ChatSession_startedAt_not_null" NOT NULL,
    "ultimoMensajeEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "ChatSession_lastMessageAt_not_null" NOT NULL
);


--
-- Name: TipoEntrada; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."TipoEntrada" (
    id text CONSTRAINT "TicketType_id_not_null" NOT NULL,
    "eventoId" text CONSTRAINT "TicketType_eventId_not_null" NOT NULL,
    nombre text CONSTRAINT "TicketType_name_not_null" NOT NULL,
    descripcion text,
    "precioCentimos" integer CONSTRAINT "TicketType_priceCents_not_null" NOT NULL,
    cantidad integer CONSTRAINT "TicketType_quantity_not_null" NOT NULL,
    vendidas integer DEFAULT 0 CONSTRAINT "TicketType_sold_not_null" NOT NULL,
    "ventaDesde" timestamp(3) without time zone,
    "ventaHasta" timestamp(3) without time zone,
    "maxPorPedido" integer DEFAULT 6 CONSTRAINT "TicketType_maxPerOrder_not_null" NOT NULL,
    "incluyeConsumo" integer DEFAULT 0 CONSTRAINT "TicketType_includesDrink_not_null" NOT NULL,
    activo boolean DEFAULT true CONSTRAINT "TicketType_active_not_null" NOT NULL
);


--
-- Name: Turno; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Turno" (
    id text CONSTRAINT "Shift_id_not_null" NOT NULL,
    "usuarioId" text CONSTRAINT "Shift_employeeId_not_null" NOT NULL,
    "eventoId" text,
    "puestoId" text CONSTRAINT "Shift_positionId_not_null" NOT NULL,
    "empiezaEn" timestamp(3) without time zone CONSTRAINT "Shift_startsAt_not_null" NOT NULL,
    "terminaEn" timestamp(3) without time zone CONSTRAINT "Shift_endsAt_not_null" NOT NULL,
    publicado boolean DEFAULT false CONSTRAINT "Shift_published_not_null" NOT NULL
);


--
-- Name: Usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Usuario" (
    id text CONSTRAINT "User_id_not_null" NOT NULL,
    correo text,
    "hashClave" text,
    nombre text CONSTRAINT "User_name_not_null" NOT NULL,
    rol public."Rol" DEFAULT 'PERSONAL'::public."Rol" CONSTRAINT "User_role_not_null" NOT NULL,
    activo boolean DEFAULT true CONSTRAINT "User_active_not_null" NOT NULL,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "User_createdAt_not_null" NOT NULL,
    "puestoId" text,
    "fechaAlta" date,
    "tarifaHoraCentimos" integer DEFAULT 0 NOT NULL,
    "codigoPromotor" text,
    "comisionPct" integer
);


--
-- Name: VentaBarra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."VentaBarra" (
    id text CONSTRAINT "BarSale_id_not_null" NOT NULL,
    "sesionCajaId" text CONSTRAINT "BarSale_cashSessionId_not_null" NOT NULL,
    "eventoId" text,
    "totalCentimos" integer CONSTRAINT "BarSale_totalCents_not_null" NOT NULL,
    "metodoPago" public."MetodoPago" DEFAULT 'EFECTIVO'::public."MetodoPago" CONSTRAINT "BarSale_paymentMethod_not_null" NOT NULL,
    "creadoEn" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP CONSTRAINT "BarSale_createdAt_not_null" NOT NULL
);


--
-- Name: Zona; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Zona" (
    id text CONSTRAINT "Zone_id_not_null" NOT NULL,
    "salaId" text CONSTRAINT "Zone_venueId_not_null" NOT NULL,
    nombre text CONSTRAINT "Zone_name_not_null" NOT NULL,
    aforo integer DEFAULT 0 CONSTRAINT "Zone_capacity_not_null" NOT NULL,
    orden integer DEFAULT 0 CONSTRAINT "Zone_sortOrder_not_null" NOT NULL,
    fondo double precision DEFAULT 0 CONSTRAINT "Zone_depth_not_null" NOT NULL,
    alto double precision DEFAULT 3.5 CONSTRAINT "Zone_height_not_null" NOT NULL,
    tono integer DEFAULT '-1'::integer CONSTRAINT "Zone_hue_not_null" NOT NULL,
    ancho double precision DEFAULT 0 CONSTRAINT "Zone_width_not_null" NOT NULL,
    x double precision DEFAULT 0 CONSTRAINT "Zone_x_not_null" NOT NULL,
    y double precision DEFAULT 0 CONSTRAINT "Zone_y_not_null" NOT NULL,
    tipo public."TipoZona" DEFAULT 'OTRA'::public."TipoZona" NOT NULL
);


--
-- Name: Acceso Acceso_entradaId_eventoId_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Acceso"
    ADD CONSTRAINT "Acceso_entradaId_eventoId_key" UNIQUE ("entradaId", "eventoId");


--
-- Name: Acceso Acceso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Acceso"
    ADD CONSTRAINT "Acceso_pkey" PRIMARY KEY (id);


--
-- Name: Artista Artista_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Artista"
    ADD CONSTRAINT "Artista_pkey" PRIMARY KEY (id);


--
-- Name: Box Box_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Box"
    ADD CONSTRAINT "Box_pkey" PRIMARY KEY (id);


--
-- Name: Campania Campania_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Campania"
    ADD CONSTRAINT "Campania_pkey" PRIMARY KEY (id);


--
-- Name: CategoriaGasto CategoriaGasto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CategoriaGasto"
    ADD CONSTRAINT "CategoriaGasto_pkey" PRIMARY KEY (id);


--
-- Name: CategoriaProducto CategoriaProducto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CategoriaProducto"
    ADD CONSTRAINT "CategoriaProducto_pkey" PRIMARY KEY (id);


--
-- Name: Cliente Cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Cliente"
    ADD CONSTRAINT "Cliente_pkey" PRIMARY KEY (id);


--
-- Name: EjecucionAgente EjecucionAgente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EjecucionAgente"
    ADD CONSTRAINT "EjecucionAgente_pkey" PRIMARY KEY (id);


--
-- Name: Entrada Entrada_id_eventoId_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Entrada"
    ADD CONSTRAINT "Entrada_id_eventoId_key" UNIQUE (id, "eventoId");


--
-- Name: Entrada Entrada_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Entrada"
    ADD CONSTRAINT "Entrada_pkey" PRIMARY KEY (id);


--
-- Name: EventoArtista EventoArtista_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EventoArtista"
    ADD CONSTRAINT "EventoArtista_pkey" PRIMARY KEY ("eventoId", "artistaId");


--
-- Name: Evento Evento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Evento"
    ADD CONSTRAINT "Evento_pkey" PRIMARY KEY (id);


--
-- Name: Gasto Gasto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Gasto"
    ADD CONSTRAINT "Gasto_pkey" PRIMARY KEY (id);


--
-- Name: Interaccion Interaccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Interaccion"
    ADD CONSTRAINT "Interaccion_pkey" PRIMARY KEY (id);


--
-- Name: Invitado Invitado_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Invitado"
    ADD CONSTRAINT "Invitado_pkey" PRIMARY KEY (id);


--
-- Name: LineaOrdenCompra LineaOrdenCompra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaOrdenCompra"
    ADD CONSTRAINT "LineaOrdenCompra_pkey" PRIMARY KEY (id);


--
-- Name: LineaPedido LineaPedido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaPedido"
    ADD CONSTRAINT "LineaPedido_pkey" PRIMARY KEY (id);


--
-- Name: LineaVentaBarra LineaVentaBarra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaVentaBarra"
    ADD CONSTRAINT "LineaVentaBarra_pkey" PRIMARY KEY (id);


--
-- Name: ListaInvitados ListaInvitados_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ListaInvitados"
    ADD CONSTRAINT "ListaInvitados_pkey" PRIMARY KEY (id);


--
-- Name: MensajeChat MensajeChat_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MensajeChat"
    ADD CONSTRAINT "MensajeChat_pkey" PRIMARY KEY (id);


--
-- Name: MovimientoFidelidad MovimientoFidelidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MovimientoFidelidad"
    ADD CONSTRAINT "MovimientoFidelidad_pkey" PRIMARY KEY (id);


--
-- Name: MovimientoStock MovimientoStock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MovimientoStock"
    ADD CONSTRAINT "MovimientoStock_pkey" PRIMARY KEY (id);


--
-- Name: OrdenCompra OrdenCompra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."OrdenCompra"
    ADD CONSTRAINT "OrdenCompra_pkey" PRIMARY KEY (id);


--
-- Name: Pedido Pedido_id_eventoId_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Pedido"
    ADD CONSTRAINT "Pedido_id_eventoId_key" UNIQUE (id, "eventoId");


--
-- Name: Pedido Pedido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Pedido"
    ADD CONSTRAINT "Pedido_pkey" PRIMARY KEY (id);


--
-- Name: Producto Producto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Producto"
    ADD CONSTRAINT "Producto_pkey" PRIMARY KEY (id);


--
-- Name: PropuestaAgente PropuestaAgente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PropuestaAgente"
    ADD CONSTRAINT "PropuestaAgente_pkey" PRIMARY KEY (id);


--
-- Name: Proveedor Proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Proveedor"
    ADD CONSTRAINT "Proveedor_pkey" PRIMARY KEY (id);


--
-- Name: Puerta Puerta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Puerta"
    ADD CONSTRAINT "Puerta_pkey" PRIMARY KEY (id);


--
-- Name: Puesto Puesto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Puesto"
    ADD CONSTRAINT "Puesto_pkey" PRIMARY KEY (id);


--
-- Name: ReservaBox ReservaBox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ReservaBox"
    ADD CONSTRAINT "ReservaBox_pkey" PRIMARY KEY (id);


--
-- Name: Sala Sala_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Sala"
    ADD CONSTRAINT "Sala_pkey" PRIMARY KEY (id);


--
-- Name: Segmento Segmento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Segmento"
    ADD CONSTRAINT "Segmento_pkey" PRIMARY KEY (id);


--
-- Name: SesionCaja SesionCaja_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."SesionCaja"
    ADD CONSTRAINT "SesionCaja_pkey" PRIMARY KEY (id);


--
-- Name: SesionChat SesionChat_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."SesionChat"
    ADD CONSTRAINT "SesionChat_pkey" PRIMARY KEY (id);


--
-- Name: TipoEntrada TipoEntrada_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TipoEntrada"
    ADD CONSTRAINT "TipoEntrada_pkey" PRIMARY KEY (id);


--
-- Name: Turno Turno_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Turno"
    ADD CONSTRAINT "Turno_pkey" PRIMARY KEY (id);


--
-- Name: Usuario Usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Usuario"
    ADD CONSTRAINT "Usuario_pkey" PRIMARY KEY (id);


--
-- Name: VentaBarra VentaBarra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."VentaBarra"
    ADD CONSTRAINT "VentaBarra_pkey" PRIMARY KEY (id);


--
-- Name: Zona Zona_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Zona"
    ADD CONSTRAINT "Zona_pkey" PRIMARY KEY (id);


--
-- Name: Acceso_entradaId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Acceso_entradaId_key" ON public."Acceso" USING btree ("entradaId");


--
-- Name: Acceso_eventoId_escaneadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Acceso_eventoId_escaneadoEn_idx" ON public."Acceso" USING btree ("eventoId", "escaneadoEn");


--
-- Name: Acceso_puertaId_escaneadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Acceso_puertaId_escaneadoEn_idx" ON public."Acceso" USING btree ("puertaId", "escaneadoEn");


--
-- Name: Box_zonaId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Box_zonaId_idx" ON public."Box" USING btree ("zonaId");


--
-- Name: Campania_ejecucionId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Campania_ejecucionId_idx" ON public."Campania" USING btree ("ejecucionId");


--
-- Name: Campania_segmentoId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Campania_segmentoId_idx" ON public."Campania" USING btree ("segmentoId");


--
-- Name: CategoriaGasto_nombre_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "CategoriaGasto_nombre_key" ON public."CategoriaGasto" USING btree (nombre);


--
-- Name: CategoriaProducto_nombre_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "CategoriaProducto_nombre_key" ON public."CategoriaProducto" USING btree (nombre);


--
-- Name: Cliente_correo_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Cliente_correo_key" ON public."Cliente" USING btree (correo);


--
-- Name: Cliente_fechaNacimiento_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Cliente_fechaNacimiento_idx" ON public."Cliente" USING btree ("fechaNacimiento");


--
-- Name: Cliente_ultimaVisitaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Cliente_ultimaVisitaEn_idx" ON public."Cliente" USING btree ("ultimaVisitaEn");


--
-- Name: EjecucionAgente_claveAgente_iniciadaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "EjecucionAgente_claveAgente_iniciadaEn_idx" ON public."EjecucionAgente" USING btree ("claveAgente", "iniciadaEn");


--
-- Name: EjecucionAgente_disparador_iniciadaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "EjecucionAgente_disparador_iniciadaEn_idx" ON public."EjecucionAgente" USING btree (disparador, "iniciadaEn");


--
-- Name: EjecucionAgente_lanzadoPorId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "EjecucionAgente_lanzadoPorId_idx" ON public."EjecucionAgente" USING btree ("lanzadoPorId");


--
-- Name: EjecucionAgente_modelo_iniciadaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "EjecucionAgente_modelo_iniciadaEn_idx" ON public."EjecucionAgente" USING btree (modelo, "iniciadaEn");


--
-- Name: Entrada_codigo_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Entrada_codigo_key" ON public."Entrada" USING btree (codigo);


--
-- Name: Entrada_eventoId_estado_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Entrada_eventoId_estado_idx" ON public."Entrada" USING btree ("eventoId", estado);


--
-- Name: Evento_empiezaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Evento_empiezaEn_idx" ON public."Evento" USING btree ("empiezaEn");


--
-- Name: Evento_estado_empiezaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Evento_estado_empiezaEn_idx" ON public."Evento" USING btree (estado, "empiezaEn");


--
-- Name: Evento_slug_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Evento_slug_key" ON public."Evento" USING btree (slug);


--
-- Name: Gasto_categoriaId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Gasto_categoriaId_idx" ON public."Gasto" USING btree ("categoriaId");


--
-- Name: Gasto_eventoId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Gasto_eventoId_idx" ON public."Gasto" USING btree ("eventoId");


--
-- Name: Gasto_fechaGasto_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Gasto_fechaGasto_idx" ON public."Gasto" USING btree ("fechaGasto");


--
-- Name: Interaccion_clienteId_ocurrioEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Interaccion_clienteId_ocurrioEn_idx" ON public."Interaccion" USING btree ("clienteId", "ocurrioEn");


--
-- Name: Invitado_listaInvitadosId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Invitado_listaInvitadosId_idx" ON public."Invitado" USING btree ("listaInvitadosId");


--
-- Name: LineaOrdenCompra_productoId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "LineaOrdenCompra_productoId_idx" ON public."LineaOrdenCompra" USING btree ("productoId");


--
-- Name: LineaPedido_tipoEntradaId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "LineaPedido_tipoEntradaId_idx" ON public."LineaPedido" USING btree ("tipoEntradaId");


--
-- Name: LineaVentaBarra_productoId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "LineaVentaBarra_productoId_idx" ON public."LineaVentaBarra" USING btree ("productoId");


--
-- Name: LineaVentaBarra_ventaBarraId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "LineaVentaBarra_ventaBarraId_idx" ON public."LineaVentaBarra" USING btree ("ventaBarraId");


--
-- Name: ListaInvitados_eventoId_promotorId_nombre_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "ListaInvitados_eventoId_promotorId_nombre_key" ON public."ListaInvitados" USING btree ("eventoId", "promotorId", nombre);


--
-- Name: MensajeChat_ejecucionId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "MensajeChat_ejecucionId_idx" ON public."MensajeChat" USING btree ("ejecucionId");


--
-- Name: MensajeChat_sesionId_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "MensajeChat_sesionId_creadoEn_idx" ON public."MensajeChat" USING btree ("sesionId", "creadoEn");


--
-- Name: MovimientoFidelidad_clienteId_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "MovimientoFidelidad_clienteId_creadoEn_idx" ON public."MovimientoFidelidad" USING btree ("clienteId", "creadoEn");


--
-- Name: MovimientoStock_productoId_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "MovimientoStock_productoId_creadoEn_idx" ON public."MovimientoStock" USING btree ("productoId", "creadoEn");


--
-- Name: MovimientoStock_salaId_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "MovimientoStock_salaId_creadoEn_idx" ON public."MovimientoStock" USING btree ("salaId", "creadoEn");


--
-- Name: OrdenCompra_estado_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "OrdenCompra_estado_creadoEn_idx" ON public."OrdenCompra" USING btree (estado, "creadoEn");


--
-- Name: OrdenCompra_proveedorId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "OrdenCompra_proveedorId_idx" ON public."OrdenCompra" USING btree ("proveedorId");


--
-- Name: Pedido_clienteId_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Pedido_clienteId_creadoEn_idx" ON public."Pedido" USING btree ("clienteId", "creadoEn");


--
-- Name: Pedido_codigo_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Pedido_codigo_key" ON public."Pedido" USING btree (codigo);


--
-- Name: Pedido_estado_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Pedido_estado_creadoEn_idx" ON public."Pedido" USING btree (estado, "creadoEn");


--
-- Name: Pedido_eventoId_estado_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Pedido_eventoId_estado_idx" ON public."Pedido" USING btree ("eventoId", estado);


--
-- Name: Producto_categoriaId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Producto_categoriaId_idx" ON public."Producto" USING btree ("categoriaId");


--
-- Name: Producto_sku_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Producto_sku_key" ON public."Producto" USING btree (sku);


--
-- Name: PropuestaAgente_ejecucionId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "PropuestaAgente_ejecucionId_idx" ON public."PropuestaAgente" USING btree ("ejecucionId");


--
-- Name: PropuestaAgente_estado_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "PropuestaAgente_estado_creadoEn_idx" ON public."PropuestaAgente" USING btree (estado, "creadoEn");


--
-- Name: PropuestaAgente_tipo_estado_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "PropuestaAgente_tipo_estado_idx" ON public."PropuestaAgente" USING btree (tipo, estado);


--
-- Name: Puerta_salaId_nombre_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Puerta_salaId_nombre_key" ON public."Puerta" USING btree ("salaId", nombre);


--
-- Name: Puesto_nombre_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Puesto_nombre_key" ON public."Puesto" USING btree (nombre);


--
-- Name: ReservaBox_clienteId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ReservaBox_clienteId_idx" ON public."ReservaBox" USING btree ("clienteId");


--
-- Name: ReservaBox_eventoId_boxId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "ReservaBox_eventoId_boxId_key" ON public."ReservaBox" USING btree ("eventoId", "boxId");


--
-- Name: Segmento_nombre_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Segmento_nombre_key" ON public."Segmento" USING btree (nombre);


--
-- Name: SesionCaja_salaId_abiertaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "SesionCaja_salaId_abiertaEn_idx" ON public."SesionCaja" USING btree ("salaId", "abiertaEn");


--
-- Name: SesionCaja_usuarioId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "SesionCaja_usuarioId_idx" ON public."SesionCaja" USING btree ("usuarioId");


--
-- Name: SesionChat_clienteId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "SesionChat_clienteId_idx" ON public."SesionChat" USING btree ("clienteId");


--
-- Name: SesionChat_ultimoMensajeEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "SesionChat_ultimoMensajeEn_idx" ON public."SesionChat" USING btree ("ultimoMensajeEn");


--
-- Name: TipoEntrada_eventoId_activo_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "TipoEntrada_eventoId_activo_idx" ON public."TipoEntrada" USING btree ("eventoId", activo);


--
-- Name: Turno_eventoId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Turno_eventoId_idx" ON public."Turno" USING btree ("eventoId");


--
-- Name: Turno_puestoId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Turno_puestoId_idx" ON public."Turno" USING btree ("puestoId");


--
-- Name: Turno_usuarioId_empiezaEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Turno_usuarioId_empiezaEn_idx" ON public."Turno" USING btree ("usuarioId", "empiezaEn");


--
-- Name: Usuario_codigoPromotor_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Usuario_codigoPromotor_key" ON public."Usuario" USING btree ("codigoPromotor");


--
-- Name: Usuario_correo_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Usuario_correo_key" ON public."Usuario" USING btree (correo);


--
-- Name: Usuario_puestoId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Usuario_puestoId_idx" ON public."Usuario" USING btree ("puestoId");


--
-- Name: VentaBarra_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "VentaBarra_creadoEn_idx" ON public."VentaBarra" USING btree ("creadoEn");


--
-- Name: VentaBarra_eventoId_creadoEn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "VentaBarra_eventoId_creadoEn_idx" ON public."VentaBarra" USING btree ("eventoId", "creadoEn");


--
-- Name: VentaBarra_sesionCajaId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "VentaBarra_sesionCajaId_idx" ON public."VentaBarra" USING btree ("sesionCajaId");


--
-- Name: Zona_salaId_nombre_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Zona_salaId_nombre_key" ON public."Zona" USING btree ("salaId", nombre);


--
-- Name: Acceso Acceso_entradaId_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Acceso"
    ADD CONSTRAINT "Acceso_entradaId_eventoId_fkey" FOREIGN KEY ("entradaId", "eventoId") REFERENCES public."Entrada"(id, "eventoId") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Acceso Acceso_escaneadoPorId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Acceso"
    ADD CONSTRAINT "Acceso_escaneadoPorId_fkey" FOREIGN KEY ("escaneadoPorId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Acceso Acceso_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Acceso"
    ADD CONSTRAINT "Acceso_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Acceso Acceso_puertaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Acceso"
    ADD CONSTRAINT "Acceso_puertaId_fkey" FOREIGN KEY ("puertaId") REFERENCES public."Puerta"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Box Box_salaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Box"
    ADD CONSTRAINT "Box_salaId_fkey" FOREIGN KEY ("salaId") REFERENCES public."Sala"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Box Box_zonaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Box"
    ADD CONSTRAINT "Box_zonaId_fkey" FOREIGN KEY ("zonaId") REFERENCES public."Zona"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Campania Campania_ejecucionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Campania"
    ADD CONSTRAINT "Campania_ejecucionId_fkey" FOREIGN KEY ("ejecucionId") REFERENCES public."EjecucionAgente"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Campania Campania_segmentoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Campania"
    ADD CONSTRAINT "Campania_segmentoId_fkey" FOREIGN KEY ("segmentoId") REFERENCES public."Segmento"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: EjecucionAgente EjecucionAgente_lanzadoPorId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EjecucionAgente"
    ADD CONSTRAINT "EjecucionAgente_lanzadoPorId_fkey" FOREIGN KEY ("lanzadoPorId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Entrada Entrada_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Entrada"
    ADD CONSTRAINT "Entrada_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Entrada Entrada_pedidoId_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Entrada"
    ADD CONSTRAINT "Entrada_pedidoId_eventoId_fkey" FOREIGN KEY ("pedidoId", "eventoId") REFERENCES public."Pedido"(id, "eventoId") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Entrada Entrada_tipoEntradaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Entrada"
    ADD CONSTRAINT "Entrada_tipoEntradaId_fkey" FOREIGN KEY ("tipoEntradaId") REFERENCES public."TipoEntrada"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: EventoArtista EventoArtista_artistaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EventoArtista"
    ADD CONSTRAINT "EventoArtista_artistaId_fkey" FOREIGN KEY ("artistaId") REFERENCES public."Artista"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: EventoArtista EventoArtista_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EventoArtista"
    ADD CONSTRAINT "EventoArtista_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Evento Evento_salaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Evento"
    ADD CONSTRAINT "Evento_salaId_fkey" FOREIGN KEY ("salaId") REFERENCES public."Sala"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Gasto Gasto_categoriaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Gasto"
    ADD CONSTRAINT "Gasto_categoriaId_fkey" FOREIGN KEY ("categoriaId") REFERENCES public."CategoriaGasto"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Gasto Gasto_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Gasto"
    ADD CONSTRAINT "Gasto_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Gasto Gasto_usuarioId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Gasto"
    ADD CONSTRAINT "Gasto_usuarioId_fkey" FOREIGN KEY ("usuarioId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Interaccion Interaccion_clienteId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Interaccion"
    ADD CONSTRAINT "Interaccion_clienteId_fkey" FOREIGN KEY ("clienteId") REFERENCES public."Cliente"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Invitado Invitado_listaInvitadosId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Invitado"
    ADD CONSTRAINT "Invitado_listaInvitadosId_fkey" FOREIGN KEY ("listaInvitadosId") REFERENCES public."ListaInvitados"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: LineaOrdenCompra LineaOrdenCompra_ordenCompraId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaOrdenCompra"
    ADD CONSTRAINT "LineaOrdenCompra_ordenCompraId_fkey" FOREIGN KEY ("ordenCompraId") REFERENCES public."OrdenCompra"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: LineaOrdenCompra LineaOrdenCompra_productoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaOrdenCompra"
    ADD CONSTRAINT "LineaOrdenCompra_productoId_fkey" FOREIGN KEY ("productoId") REFERENCES public."Producto"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: LineaPedido LineaPedido_pedidoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaPedido"
    ADD CONSTRAINT "LineaPedido_pedidoId_fkey" FOREIGN KEY ("pedidoId") REFERENCES public."Pedido"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: LineaPedido LineaPedido_tipoEntradaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaPedido"
    ADD CONSTRAINT "LineaPedido_tipoEntradaId_fkey" FOREIGN KEY ("tipoEntradaId") REFERENCES public."TipoEntrada"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: LineaVentaBarra LineaVentaBarra_productoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaVentaBarra"
    ADD CONSTRAINT "LineaVentaBarra_productoId_fkey" FOREIGN KEY ("productoId") REFERENCES public."Producto"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: LineaVentaBarra LineaVentaBarra_ventaBarraId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LineaVentaBarra"
    ADD CONSTRAINT "LineaVentaBarra_ventaBarraId_fkey" FOREIGN KEY ("ventaBarraId") REFERENCES public."VentaBarra"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: ListaInvitados ListaInvitados_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ListaInvitados"
    ADD CONSTRAINT "ListaInvitados_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: ListaInvitados ListaInvitados_promotorId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ListaInvitados"
    ADD CONSTRAINT "ListaInvitados_promotorId_fkey" FOREIGN KEY ("promotorId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: MensajeChat MensajeChat_ejecucionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MensajeChat"
    ADD CONSTRAINT "MensajeChat_ejecucionId_fkey" FOREIGN KEY ("ejecucionId") REFERENCES public."EjecucionAgente"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: MensajeChat MensajeChat_sesionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MensajeChat"
    ADD CONSTRAINT "MensajeChat_sesionId_fkey" FOREIGN KEY ("sesionId") REFERENCES public."SesionChat"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: MovimientoFidelidad MovimientoFidelidad_clienteId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MovimientoFidelidad"
    ADD CONSTRAINT "MovimientoFidelidad_clienteId_fkey" FOREIGN KEY ("clienteId") REFERENCES public."Cliente"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: MovimientoFidelidad MovimientoFidelidad_pedidoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MovimientoFidelidad"
    ADD CONSTRAINT "MovimientoFidelidad_pedidoId_fkey" FOREIGN KEY ("pedidoId") REFERENCES public."Pedido"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: MovimientoStock MovimientoStock_productoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MovimientoStock"
    ADD CONSTRAINT "MovimientoStock_productoId_fkey" FOREIGN KEY ("productoId") REFERENCES public."Producto"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: MovimientoStock MovimientoStock_salaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MovimientoStock"
    ADD CONSTRAINT "MovimientoStock_salaId_fkey" FOREIGN KEY ("salaId") REFERENCES public."Sala"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: MovimientoStock MovimientoStock_usuarioId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."MovimientoStock"
    ADD CONSTRAINT "MovimientoStock_usuarioId_fkey" FOREIGN KEY ("usuarioId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: OrdenCompra OrdenCompra_proveedorId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."OrdenCompra"
    ADD CONSTRAINT "OrdenCompra_proveedorId_fkey" FOREIGN KEY ("proveedorId") REFERENCES public."Proveedor"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Pedido Pedido_clienteId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Pedido"
    ADD CONSTRAINT "Pedido_clienteId_fkey" FOREIGN KEY ("clienteId") REFERENCES public."Cliente"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Pedido Pedido_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Pedido"
    ADD CONSTRAINT "Pedido_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Pedido Pedido_promotorId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Pedido"
    ADD CONSTRAINT "Pedido_promotorId_fkey" FOREIGN KEY ("promotorId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Producto Producto_categoriaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Producto"
    ADD CONSTRAINT "Producto_categoriaId_fkey" FOREIGN KEY ("categoriaId") REFERENCES public."CategoriaProducto"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: PropuestaAgente PropuestaAgente_ejecucionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PropuestaAgente"
    ADD CONSTRAINT "PropuestaAgente_ejecucionId_fkey" FOREIGN KEY ("ejecucionId") REFERENCES public."EjecucionAgente"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: PropuestaAgente PropuestaAgente_revisadaPorId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PropuestaAgente"
    ADD CONSTRAINT "PropuestaAgente_revisadaPorId_fkey" FOREIGN KEY ("revisadaPorId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Puerta Puerta_salaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Puerta"
    ADD CONSTRAINT "Puerta_salaId_fkey" FOREIGN KEY ("salaId") REFERENCES public."Sala"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: ReservaBox ReservaBox_boxId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ReservaBox"
    ADD CONSTRAINT "ReservaBox_boxId_fkey" FOREIGN KEY ("boxId") REFERENCES public."Box"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ReservaBox ReservaBox_clienteId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ReservaBox"
    ADD CONSTRAINT "ReservaBox_clienteId_fkey" FOREIGN KEY ("clienteId") REFERENCES public."Cliente"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ReservaBox ReservaBox_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ReservaBox"
    ADD CONSTRAINT "ReservaBox_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: SesionCaja SesionCaja_salaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."SesionCaja"
    ADD CONSTRAINT "SesionCaja_salaId_fkey" FOREIGN KEY ("salaId") REFERENCES public."Sala"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: SesionCaja SesionCaja_usuarioId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."SesionCaja"
    ADD CONSTRAINT "SesionCaja_usuarioId_fkey" FOREIGN KEY ("usuarioId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: SesionChat SesionChat_clienteId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."SesionChat"
    ADD CONSTRAINT "SesionChat_clienteId_fkey" FOREIGN KEY ("clienteId") REFERENCES public."Cliente"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: TipoEntrada TipoEntrada_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TipoEntrada"
    ADD CONSTRAINT "TipoEntrada_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Turno Turno_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Turno"
    ADD CONSTRAINT "Turno_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Turno Turno_puestoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Turno"
    ADD CONSTRAINT "Turno_puestoId_fkey" FOREIGN KEY ("puestoId") REFERENCES public."Puesto"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Turno Turno_usuarioId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Turno"
    ADD CONSTRAINT "Turno_usuarioId_fkey" FOREIGN KEY ("usuarioId") REFERENCES public."Usuario"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Usuario Usuario_puestoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Usuario"
    ADD CONSTRAINT "Usuario_puestoId_fkey" FOREIGN KEY ("puestoId") REFERENCES public."Puesto"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: VentaBarra VentaBarra_eventoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."VentaBarra"
    ADD CONSTRAINT "VentaBarra_eventoId_fkey" FOREIGN KEY ("eventoId") REFERENCES public."Evento"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: VentaBarra VentaBarra_sesionCajaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."VentaBarra"
    ADD CONSTRAINT "VentaBarra_sesionCajaId_fkey" FOREIGN KEY ("sesionCajaId") REFERENCES public."SesionCaja"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Zona Zona_salaId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Zona"
    ADD CONSTRAINT "Zona_salaId_fkey" FOREIGN KEY ("salaId") REFERENCES public."Sala"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--


