-- ─────────────────────────────────────────────────────────────────────────
--  Esquema inicial.
--
--  No esta escrito a mano: es el volcado de la base `discoteca` tal y como la
--  dejo el proyecto anterior (Prisma), con `pg_dump --schema-only`. A partir de
--  aqui el dueno del esquema es Flyway y los cambios van en V2, V3...
--
--  43 tablas, 20 tipos enum, 109 restricciones y 74 indices.
--
--  Sobre una base que YA tiene estas tablas, Flyway no ejecuta este archivo:
--  `baseline-on-migrate` la marca como version 1 ya aplicada. Sobre una base
--  vacia si se ejecuta, y deja el esquema completo listo.
--
--  Se le quitaron al volcado las lineas `\restrict` y `\unrestrict` que anade
--  pg_dump 18: son metacomandos de psql, no SQL, y Flyway no los entiende.
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
-- Name: AgentKey; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."AgentKey" AS ENUM (
    'marketing',
    'crm-segmenter',
    'inventory',
    'pricing',
    'reporter',
    'concierge',
    'inventory-ml'
);


--
-- Name: AgentRunStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."AgentRunStatus" AS ENUM (
    'QUEUED',
    'RUNNING',
    'SUCCEEDED',
    'FAILED'
);


--
-- Name: AgentTrigger; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."AgentTrigger" AS ENUM (
    'manual',
    'scheduled',
    'chat',
    'benchmark'
);


--
-- Name: CampaignChannel; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."CampaignChannel" AS ENUM (
    'EMAIL',
    'SMS',
    'WHATSAPP',
    'PUSH'
);


--
-- Name: CampaignStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."CampaignStatus" AS ENUM (
    'DRAFT',
    'SCHEDULED',
    'SENT',
    'CANCELLED'
);


--
-- Name: ChatChannel; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ChatChannel" AS ENUM (
    'WEB',
    'WHATSAPP',
    'INSTAGRAM'
);


--
-- Name: ChatRole; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ChatRole" AS ENUM (
    'user',
    'assistant',
    'system'
);


--
-- Name: EventStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."EventStatus" AS ENUM (
    'DRAFT',
    'PUBLISHED',
    'SOLD_OUT',
    'CANCELLED',
    'FINISHED'
);


--
-- Name: InteractionChannel; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."InteractionChannel" AS ENUM (
    'WEB',
    'WHATSAPP',
    'EMAIL',
    'PHONE',
    'INSTAGRAM',
    'IN_PERSON'
);


--
-- Name: InteractionType; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."InteractionType" AS ENUM (
    'PURCHASE',
    'VISIT',
    'RESERVATION',
    'CAMPAIGN',
    'CHAT',
    'COMPLAINT',
    'NOTE'
);


--
-- Name: OrderStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."OrderStatus" AS ENUM (
    'PENDING',
    'PAID',
    'CANCELLED',
    'REFUNDED'
);


--
-- Name: PaymentMethod; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."PaymentMethod" AS ENUM (
    'CASH',
    'CARD',
    'YAPE',
    'PLIN',
    'TRANSFER'
);


--
-- Name: ProductUnit; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ProductUnit" AS ENUM (
    'UNIDAD',
    'BOTELLA',
    'CAJA',
    'LITRO'
);


--
-- Name: ProposalKind; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ProposalKind" AS ENUM (
    'CAMPAIGN_COPY',
    'CAMPAIGN',
    'PURCHASE_ORDER',
    'PRICE_CHANGE',
    'REPORT'
);


--
-- Name: ProposalStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ProposalStatus" AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED'
);


--
-- Name: PurchaseOrderStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."PurchaseOrderStatus" AS ENUM (
    'DRAFT',
    'SENT',
    'RECEIVED',
    'CANCELLED'
);


--
-- Name: ReservationStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ReservationStatus" AS ENUM (
    'REQUESTED',
    'CONFIRMED',
    'SEATED',
    'NO_SHOW',
    'CANCELLED'
);


--
-- Name: Role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."Role" AS ENUM (
    'ADMIN',
    'MANAGER',
    'PROMOTER',
    'DOOR',
    'BAR',
    'STAFF',
    'CUSTOMER'
);


--
-- Name: StockMovementType; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."StockMovementType" AS ENUM (
    'PURCHASE',
    'SALE',
    'WASTE',
    'ADJUSTMENT',
    'TRANSFER'
);


--
-- Name: TicketStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."TicketStatus" AS ENUM (
    'VALID',
    'USED',
    'VOID',
    'REFUNDED'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: AgentProposal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."AgentProposal" (
    id text NOT NULL,
    "agentRunId" text NOT NULL,
    kind public."ProposalKind" NOT NULL,
    payload jsonb NOT NULL,
    status public."ProposalStatus" DEFAULT 'PENDING'::public."ProposalStatus" NOT NULL,
    "reviewedById" text,
    "reviewedAt" timestamp(3) without time zone,
    "appliedAt" timestamp(3) without time zone,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: AgentRun; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."AgentRun" (
    id text NOT NULL,
    "agentKey" public."AgentKey" NOT NULL,
    trigger public."AgentTrigger" NOT NULL,
    "triggeredById" text,
    input jsonb,
    output jsonb,
    status public."AgentRunStatus" DEFAULT 'QUEUED'::public."AgentRunStatus" NOT NULL,
    model text,
    "inputTokens" integer DEFAULT 0 NOT NULL,
    "outputTokens" integer DEFAULT 0 NOT NULL,
    "costCents" integer DEFAULT 0 NOT NULL,
    error text,
    "startedAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "finishedAt" timestamp(3) without time zone,
    attempts integer DEFAULT 1 NOT NULL
);


--
-- Name: Artist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Artist" (
    id text NOT NULL,
    name text NOT NULL,
    genre text,
    "imageUrl" text,
    instagram text,
    bio text
);


--
-- Name: BarSale; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."BarSale" (
    id text NOT NULL,
    "cashSessionId" text NOT NULL,
    "eventId" text,
    "totalCents" integer NOT NULL,
    "paymentMethod" public."PaymentMethod" DEFAULT 'CASH'::public."PaymentMethod" NOT NULL,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: BarSaleItem; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."BarSaleItem" (
    id text NOT NULL,
    "barSaleId" text NOT NULL,
    "productId" text NOT NULL,
    quantity integer NOT NULL,
    "unitPriceCents" integer NOT NULL
);


--
-- Name: Campaign; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Campaign" (
    id text NOT NULL,
    name text NOT NULL,
    channel public."CampaignChannel" NOT NULL,
    "segmentId" text NOT NULL,
    subject text,
    body text NOT NULL,
    status public."CampaignStatus" DEFAULT 'DRAFT'::public."CampaignStatus" NOT NULL,
    "scheduledAt" timestamp(3) without time zone,
    "sentAt" timestamp(3) without time zone,
    "sentCount" integer DEFAULT 0 NOT NULL,
    "openCount" integer DEFAULT 0 NOT NULL,
    "clickCount" integer DEFAULT 0 NOT NULL,
    "agentRunId" text,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: CashSession; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."CashSession" (
    id text NOT NULL,
    "venueId" text NOT NULL,
    "employeeId" text NOT NULL,
    "openingCents" integer DEFAULT 0 NOT NULL,
    "expectedCents" integer DEFAULT 0 NOT NULL,
    "countedCents" integer,
    "differenceCents" integer,
    "openedAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "closedAt" timestamp(3) without time zone
);


--
-- Name: ChatMessage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."ChatMessage" (
    id text NOT NULL,
    "sessionId" text NOT NULL,
    role public."ChatRole" NOT NULL,
    content text NOT NULL,
    "agentRunId" text,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ChatSession; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."ChatSession" (
    id text NOT NULL,
    "customerId" text,
    channel public."ChatChannel" DEFAULT 'WEB'::public."ChatChannel" NOT NULL,
    "startedAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "lastMessageAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: CheckIn; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."CheckIn" (
    id text NOT NULL,
    "ticketId" text NOT NULL,
    "eventId" text NOT NULL,
    "scannedById" text NOT NULL,
    "gateId" text,
    "scannedAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Customer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Customer" (
    id text NOT NULL,
    "userId" text,
    "firstName" text NOT NULL,
    "lastName" text,
    email text NOT NULL,
    phone text,
    "birthDate" date,
    district text,
    "marketingConsent" boolean DEFAULT false NOT NULL,
    "consentAt" timestamp(3) without time zone,
    "visitCount" integer DEFAULT 0 NOT NULL,
    "totalSpentCents" integer DEFAULT 0 NOT NULL,
    "lastVisitAt" timestamp(3) without time zone,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: CustomerSegment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."CustomerSegment" (
    "customerId" text NOT NULL,
    "segmentId" text NOT NULL,
    "addedAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Employee; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Employee" (
    id text NOT NULL,
    "userId" text NOT NULL,
    "positionId" text NOT NULL,
    "hireDate" date NOT NULL,
    "hourlyRateCents" integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: Event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Event" (
    id text NOT NULL,
    slug text NOT NULL,
    title text NOT NULL,
    description text,
    "coverImage" text,
    "venueId" text NOT NULL,
    "doorsAt" timestamp(3) without time zone NOT NULL,
    "startsAt" timestamp(3) without time zone NOT NULL,
    "endsAt" timestamp(3) without time zone NOT NULL,
    "minAge" integer DEFAULT 18 NOT NULL,
    "dressCode" text,
    status public."EventStatus" DEFAULT 'DRAFT'::public."EventStatus" NOT NULL,
    "publishedAt" timestamp(3) without time zone,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: EventArtist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."EventArtist" (
    "eventId" text NOT NULL,
    "artistId" text NOT NULL,
    "setStart" timestamp(3) without time zone,
    "setEnd" timestamp(3) without time zone,
    headliner boolean DEFAULT false NOT NULL
);


--
-- Name: Expense; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Expense" (
    id text NOT NULL,
    "eventId" text,
    "categoryId" text NOT NULL,
    "amountCents" integer NOT NULL,
    description text,
    "employeeId" text,
    "incurredAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ExpenseCategory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."ExpenseCategory" (
    id text NOT NULL,
    name text NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: Gate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Gate" (
    id text NOT NULL,
    "venueId" text NOT NULL,
    name text NOT NULL,
    "isEmergencyExit" boolean DEFAULT false NOT NULL,
    "flowPerMinute" integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    width double precision DEFAULT 1.2 NOT NULL,
    x double precision DEFAULT 0 NOT NULL,
    y double precision DEFAULT 0 NOT NULL
);


--
-- Name: GuestList; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."GuestList" (
    id text NOT NULL,
    "eventId" text NOT NULL,
    "promoterId" text NOT NULL,
    name text NOT NULL,
    "maxGuests" integer DEFAULT 50 NOT NULL
);


--
-- Name: GuestListEntry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."GuestListEntry" (
    id text NOT NULL,
    "guestListId" text NOT NULL,
    "fullName" text NOT NULL,
    guests integer DEFAULT 1 NOT NULL,
    "checkedIn" boolean DEFAULT false NOT NULL,
    "checkedInAt" timestamp(3) without time zone
);


--
-- Name: Interaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Interaction" (
    id text NOT NULL,
    "customerId" text NOT NULL,
    type public."InteractionType" NOT NULL,
    channel public."InteractionChannel",
    summary text NOT NULL,
    metadata jsonb,
    "occurredAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: LoyaltyLedger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."LoyaltyLedger" (
    id text NOT NULL,
    "customerId" text NOT NULL,
    points integer NOT NULL,
    reason text NOT NULL,
    "orderId" text,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Order" (
    id text NOT NULL,
    code text NOT NULL,
    "customerId" text NOT NULL,
    "eventId" text NOT NULL,
    "promoterId" text,
    "subtotalCents" integer NOT NULL,
    "feesCents" integer DEFAULT 0 NOT NULL,
    "totalCents" integer NOT NULL,
    status public."OrderStatus" DEFAULT 'PENDING'::public."OrderStatus" NOT NULL,
    "paymentMethod" public."PaymentMethod",
    "paymentRef" text,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "paidAt" timestamp(3) without time zone
);


--
-- Name: OrderItem; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."OrderItem" (
    id text NOT NULL,
    "orderId" text NOT NULL,
    "ticketTypeId" text NOT NULL,
    quantity integer NOT NULL,
    "unitPriceCents" integer NOT NULL
);


--
-- Name: Product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Product" (
    id text NOT NULL,
    sku text NOT NULL,
    name text NOT NULL,
    "categoryId" text NOT NULL,
    unit public."ProductUnit" DEFAULT 'UNIDAD'::public."ProductUnit" NOT NULL,
    "costCents" integer DEFAULT 0 NOT NULL,
    "priceCents" integer DEFAULT 0 NOT NULL,
    "stockAlert" integer DEFAULT 10 NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: ProductCategory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."ProductCategory" (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: Promoter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Promoter" (
    id text NOT NULL,
    "userId" text,
    name text NOT NULL,
    code text NOT NULL,
    "commissionPct" integer DEFAULT 10 NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: PurchaseOrder; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."PurchaseOrder" (
    id text NOT NULL,
    "supplierId" text NOT NULL,
    status public."PurchaseOrderStatus" DEFAULT 'DRAFT'::public."PurchaseOrderStatus" NOT NULL,
    "totalCents" integer DEFAULT 0 NOT NULL,
    "expectedAt" timestamp(3) without time zone,
    "receivedAt" timestamp(3) without time zone,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: PurchaseOrderItem; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."PurchaseOrderItem" (
    id text NOT NULL,
    "purchaseOrderId" text NOT NULL,
    "productId" text NOT NULL,
    quantity integer NOT NULL,
    "unitCostCents" integer NOT NULL
);


--
-- Name: Segment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Segment" (
    id text NOT NULL,
    name text NOT NULL,
    description text,
    "rulesJson" jsonb NOT NULL,
    "isDynamic" boolean DEFAULT true NOT NULL,
    "updatedAt" timestamp(3) without time zone NOT NULL
);


--
-- Name: Shift; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Shift" (
    id text NOT NULL,
    "employeeId" text NOT NULL,
    "eventId" text,
    "positionId" text NOT NULL,
    "startsAt" timestamp(3) without time zone NOT NULL,
    "endsAt" timestamp(3) without time zone NOT NULL,
    published boolean DEFAULT false NOT NULL
);


--
-- Name: StaffPosition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."StaffPosition" (
    id text NOT NULL,
    name text NOT NULL,
    "isBar" boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: StockItem; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."StockItem" (
    id text NOT NULL,
    "productId" text NOT NULL,
    "venueId" text NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    "updatedAt" timestamp(3) without time zone NOT NULL
);


--
-- Name: StockMovement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."StockMovement" (
    id text NOT NULL,
    "productId" text NOT NULL,
    "venueId" text NOT NULL,
    type public."StockMovementType" NOT NULL,
    quantity integer NOT NULL,
    "unitCostCents" integer DEFAULT 0 NOT NULL,
    reference text,
    "employeeId" text,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Supplier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Supplier" (
    id text NOT NULL,
    name text NOT NULL,
    contact text,
    email text,
    phone text
);


--
-- Name: TableReservation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."TableReservation" (
    id text NOT NULL,
    "eventId" text NOT NULL,
    "tableId" text NOT NULL,
    "customerId" text NOT NULL,
    guests integer NOT NULL,
    "depositCents" integer DEFAULT 0 NOT NULL,
    status public."ReservationStatus" DEFAULT 'REQUESTED'::public."ReservationStatus" NOT NULL,
    notes text,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Ticket; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Ticket" (
    id text NOT NULL,
    code text NOT NULL,
    "orderId" text NOT NULL,
    "ticketTypeId" text NOT NULL,
    "eventId" text NOT NULL,
    "holderName" text,
    "holderDni" text,
    status public."TicketStatus" DEFAULT 'VALID'::public."TicketStatus" NOT NULL,
    "issuedAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TicketType; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."TicketType" (
    id text NOT NULL,
    "eventId" text NOT NULL,
    name text NOT NULL,
    description text,
    "priceCents" integer NOT NULL,
    quantity integer NOT NULL,
    sold integer DEFAULT 0 NOT NULL,
    "salesStart" timestamp(3) without time zone,
    "salesEnd" timestamp(3) without time zone,
    "maxPerOrder" integer DEFAULT 6 NOT NULL,
    "includesDrink" integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: TimeClock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."TimeClock" (
    id text NOT NULL,
    "employeeId" text NOT NULL,
    "shiftId" text,
    "clockIn" timestamp(3) without time zone NOT NULL,
    "clockOut" timestamp(3) without time zone
);


--
-- Name: User; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."User" (
    id text NOT NULL,
    email text NOT NULL,
    "passwordHash" text NOT NULL,
    name text NOT NULL,
    role public."Role" DEFAULT 'CUSTOMER'::public."Role" NOT NULL,
    active boolean DEFAULT true NOT NULL,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: Venue; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Venue" (
    id text NOT NULL,
    name text NOT NULL,
    capacity integer NOT NULL,
    description text,
    "planDepth" double precision DEFAULT 0 NOT NULL,
    "planWidth" double precision DEFAULT 0 NOT NULL
);


--
-- Name: VipTable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."VipTable" (
    id text NOT NULL,
    "venueId" text NOT NULL,
    "zoneId" text,
    name text NOT NULL,
    seats integer NOT NULL,
    "minSpendCents" integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: Zone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."Zone" (
    id text NOT NULL,
    "venueId" text NOT NULL,
    name text NOT NULL,
    capacity integer DEFAULT 0 NOT NULL,
    "sortOrder" integer DEFAULT 0 NOT NULL,
    depth double precision DEFAULT 0 NOT NULL,
    height double precision DEFAULT 3.5 NOT NULL,
    hue integer DEFAULT '-1'::integer NOT NULL,
    width double precision DEFAULT 0 NOT NULL,
    x double precision DEFAULT 0 NOT NULL,
    y double precision DEFAULT 0 NOT NULL
);


--
-- Name: AgentProposal AgentProposal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."AgentProposal"
    ADD CONSTRAINT "AgentProposal_pkey" PRIMARY KEY (id);


--
-- Name: AgentRun AgentRun_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."AgentRun"
    ADD CONSTRAINT "AgentRun_pkey" PRIMARY KEY (id);


--
-- Name: Artist Artist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Artist"
    ADD CONSTRAINT "Artist_pkey" PRIMARY KEY (id);


--
-- Name: BarSaleItem BarSaleItem_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."BarSaleItem"
    ADD CONSTRAINT "BarSaleItem_pkey" PRIMARY KEY (id);


--
-- Name: BarSale BarSale_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."BarSale"
    ADD CONSTRAINT "BarSale_pkey" PRIMARY KEY (id);


--
-- Name: Campaign Campaign_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Campaign"
    ADD CONSTRAINT "Campaign_pkey" PRIMARY KEY (id);


--
-- Name: CashSession CashSession_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CashSession"
    ADD CONSTRAINT "CashSession_pkey" PRIMARY KEY (id);


--
-- Name: ChatMessage ChatMessage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ChatMessage"
    ADD CONSTRAINT "ChatMessage_pkey" PRIMARY KEY (id);


--
-- Name: ChatSession ChatSession_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ChatSession"
    ADD CONSTRAINT "ChatSession_pkey" PRIMARY KEY (id);


--
-- Name: CheckIn CheckIn_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CheckIn"
    ADD CONSTRAINT "CheckIn_pkey" PRIMARY KEY (id);


--
-- Name: CustomerSegment CustomerSegment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CustomerSegment"
    ADD CONSTRAINT "CustomerSegment_pkey" PRIMARY KEY ("customerId", "segmentId");


--
-- Name: Customer Customer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Customer"
    ADD CONSTRAINT "Customer_pkey" PRIMARY KEY (id);


--
-- Name: Employee Employee_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Employee"
    ADD CONSTRAINT "Employee_pkey" PRIMARY KEY (id);


--
-- Name: EventArtist EventArtist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EventArtist"
    ADD CONSTRAINT "EventArtist_pkey" PRIMARY KEY ("eventId", "artistId");


--
-- Name: Event Event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Event"
    ADD CONSTRAINT "Event_pkey" PRIMARY KEY (id);


--
-- Name: ExpenseCategory ExpenseCategory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ExpenseCategory"
    ADD CONSTRAINT "ExpenseCategory_pkey" PRIMARY KEY (id);


--
-- Name: Expense Expense_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Expense"
    ADD CONSTRAINT "Expense_pkey" PRIMARY KEY (id);


--
-- Name: Gate Gate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Gate"
    ADD CONSTRAINT "Gate_pkey" PRIMARY KEY (id);


--
-- Name: GuestListEntry GuestListEntry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."GuestListEntry"
    ADD CONSTRAINT "GuestListEntry_pkey" PRIMARY KEY (id);


--
-- Name: GuestList GuestList_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."GuestList"
    ADD CONSTRAINT "GuestList_pkey" PRIMARY KEY (id);


--
-- Name: Interaction Interaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Interaction"
    ADD CONSTRAINT "Interaction_pkey" PRIMARY KEY (id);


--
-- Name: LoyaltyLedger LoyaltyLedger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LoyaltyLedger"
    ADD CONSTRAINT "LoyaltyLedger_pkey" PRIMARY KEY (id);


--
-- Name: OrderItem OrderItem_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."OrderItem"
    ADD CONSTRAINT "OrderItem_pkey" PRIMARY KEY (id);


--
-- Name: Order Order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Order"
    ADD CONSTRAINT "Order_pkey" PRIMARY KEY (id);


--
-- Name: ProductCategory ProductCategory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ProductCategory"
    ADD CONSTRAINT "ProductCategory_pkey" PRIMARY KEY (id);


--
-- Name: Product Product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Product"
    ADD CONSTRAINT "Product_pkey" PRIMARY KEY (id);


--
-- Name: Promoter Promoter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Promoter"
    ADD CONSTRAINT "Promoter_pkey" PRIMARY KEY (id);


--
-- Name: PurchaseOrderItem PurchaseOrderItem_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PurchaseOrderItem"
    ADD CONSTRAINT "PurchaseOrderItem_pkey" PRIMARY KEY (id);


--
-- Name: PurchaseOrder PurchaseOrder_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PurchaseOrder"
    ADD CONSTRAINT "PurchaseOrder_pkey" PRIMARY KEY (id);


--
-- Name: Segment Segment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Segment"
    ADD CONSTRAINT "Segment_pkey" PRIMARY KEY (id);


--
-- Name: Shift Shift_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Shift"
    ADD CONSTRAINT "Shift_pkey" PRIMARY KEY (id);


--
-- Name: StaffPosition StaffPosition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StaffPosition"
    ADD CONSTRAINT "StaffPosition_pkey" PRIMARY KEY (id);


--
-- Name: StockItem StockItem_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StockItem"
    ADD CONSTRAINT "StockItem_pkey" PRIMARY KEY (id);


--
-- Name: StockMovement StockMovement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StockMovement"
    ADD CONSTRAINT "StockMovement_pkey" PRIMARY KEY (id);


--
-- Name: Supplier Supplier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Supplier"
    ADD CONSTRAINT "Supplier_pkey" PRIMARY KEY (id);


--
-- Name: TableReservation TableReservation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TableReservation"
    ADD CONSTRAINT "TableReservation_pkey" PRIMARY KEY (id);


--
-- Name: TicketType TicketType_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TicketType"
    ADD CONSTRAINT "TicketType_pkey" PRIMARY KEY (id);


--
-- Name: Ticket Ticket_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Ticket"
    ADD CONSTRAINT "Ticket_pkey" PRIMARY KEY (id);


--
-- Name: TimeClock TimeClock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TimeClock"
    ADD CONSTRAINT "TimeClock_pkey" PRIMARY KEY (id);


--
-- Name: User User_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."User"
    ADD CONSTRAINT "User_pkey" PRIMARY KEY (id);


--
-- Name: Venue Venue_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Venue"
    ADD CONSTRAINT "Venue_pkey" PRIMARY KEY (id);


--
-- Name: VipTable VipTable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."VipTable"
    ADD CONSTRAINT "VipTable_pkey" PRIMARY KEY (id);


--
-- Name: Zone Zone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Zone"
    ADD CONSTRAINT "Zone_pkey" PRIMARY KEY (id);


--
-- Name: AgentProposal_agentRunId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "AgentProposal_agentRunId_idx" ON public."AgentProposal" USING btree ("agentRunId");


--
-- Name: AgentProposal_kind_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "AgentProposal_kind_status_idx" ON public."AgentProposal" USING btree (kind, status);


--
-- Name: AgentProposal_status_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "AgentProposal_status_createdAt_idx" ON public."AgentProposal" USING btree (status, "createdAt");


--
-- Name: AgentRun_agentKey_startedAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "AgentRun_agentKey_startedAt_idx" ON public."AgentRun" USING btree ("agentKey", "startedAt");


--
-- Name: AgentRun_model_startedAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "AgentRun_model_startedAt_idx" ON public."AgentRun" USING btree (model, "startedAt");


--
-- Name: AgentRun_trigger_startedAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "AgentRun_trigger_startedAt_idx" ON public."AgentRun" USING btree (trigger, "startedAt");


--
-- Name: AgentRun_triggeredById_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "AgentRun_triggeredById_idx" ON public."AgentRun" USING btree ("triggeredById");


--
-- Name: BarSaleItem_barSaleId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "BarSaleItem_barSaleId_idx" ON public."BarSaleItem" USING btree ("barSaleId");


--
-- Name: BarSaleItem_productId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "BarSaleItem_productId_idx" ON public."BarSaleItem" USING btree ("productId");


--
-- Name: BarSale_cashSessionId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "BarSale_cashSessionId_idx" ON public."BarSale" USING btree ("cashSessionId");


--
-- Name: BarSale_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "BarSale_createdAt_idx" ON public."BarSale" USING btree ("createdAt");


--
-- Name: BarSale_eventId_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "BarSale_eventId_createdAt_idx" ON public."BarSale" USING btree ("eventId", "createdAt");


--
-- Name: Campaign_agentRunId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Campaign_agentRunId_idx" ON public."Campaign" USING btree ("agentRunId");


--
-- Name: Campaign_segmentId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Campaign_segmentId_idx" ON public."Campaign" USING btree ("segmentId");


--
-- Name: CashSession_employeeId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "CashSession_employeeId_idx" ON public."CashSession" USING btree ("employeeId");


--
-- Name: CashSession_venueId_openedAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "CashSession_venueId_openedAt_idx" ON public."CashSession" USING btree ("venueId", "openedAt");


--
-- Name: ChatMessage_agentRunId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ChatMessage_agentRunId_idx" ON public."ChatMessage" USING btree ("agentRunId");


--
-- Name: ChatMessage_sessionId_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ChatMessage_sessionId_createdAt_idx" ON public."ChatMessage" USING btree ("sessionId", "createdAt");


--
-- Name: ChatSession_customerId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ChatSession_customerId_idx" ON public."ChatSession" USING btree ("customerId");


--
-- Name: ChatSession_lastMessageAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ChatSession_lastMessageAt_idx" ON public."ChatSession" USING btree ("lastMessageAt");


--
-- Name: CheckIn_eventId_scannedAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "CheckIn_eventId_scannedAt_idx" ON public."CheckIn" USING btree ("eventId", "scannedAt");


--
-- Name: CheckIn_gateId_scannedAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "CheckIn_gateId_scannedAt_idx" ON public."CheckIn" USING btree ("gateId", "scannedAt");


--
-- Name: CheckIn_ticketId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "CheckIn_ticketId_key" ON public."CheckIn" USING btree ("ticketId");


--
-- Name: CustomerSegment_segmentId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "CustomerSegment_segmentId_idx" ON public."CustomerSegment" USING btree ("segmentId");


--
-- Name: Customer_birthDate_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Customer_birthDate_idx" ON public."Customer" USING btree ("birthDate");


--
-- Name: Customer_email_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Customer_email_key" ON public."Customer" USING btree (email);


--
-- Name: Customer_lastVisitAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Customer_lastVisitAt_idx" ON public."Customer" USING btree ("lastVisitAt");


--
-- Name: Customer_userId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Customer_userId_key" ON public."Customer" USING btree ("userId");


--
-- Name: Employee_positionId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Employee_positionId_idx" ON public."Employee" USING btree ("positionId");


--
-- Name: Employee_userId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Employee_userId_key" ON public."Employee" USING btree ("userId");


--
-- Name: Event_slug_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Event_slug_key" ON public."Event" USING btree (slug);


--
-- Name: Event_startsAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Event_startsAt_idx" ON public."Event" USING btree ("startsAt");


--
-- Name: Event_status_startsAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Event_status_startsAt_idx" ON public."Event" USING btree (status, "startsAt");


--
-- Name: ExpenseCategory_name_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "ExpenseCategory_name_key" ON public."ExpenseCategory" USING btree (name);


--
-- Name: Expense_categoryId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Expense_categoryId_idx" ON public."Expense" USING btree ("categoryId");


--
-- Name: Expense_eventId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Expense_eventId_idx" ON public."Expense" USING btree ("eventId");


--
-- Name: Expense_incurredAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Expense_incurredAt_idx" ON public."Expense" USING btree ("incurredAt");


--
-- Name: Gate_venueId_name_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Gate_venueId_name_key" ON public."Gate" USING btree ("venueId", name);


--
-- Name: GuestListEntry_guestListId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "GuestListEntry_guestListId_idx" ON public."GuestListEntry" USING btree ("guestListId");


--
-- Name: GuestList_eventId_promoterId_name_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "GuestList_eventId_promoterId_name_key" ON public."GuestList" USING btree ("eventId", "promoterId", name);


--
-- Name: Interaction_customerId_occurredAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Interaction_customerId_occurredAt_idx" ON public."Interaction" USING btree ("customerId", "occurredAt");


--
-- Name: LoyaltyLedger_customerId_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "LoyaltyLedger_customerId_createdAt_idx" ON public."LoyaltyLedger" USING btree ("customerId", "createdAt");


--
-- Name: OrderItem_ticketTypeId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "OrderItem_ticketTypeId_idx" ON public."OrderItem" USING btree ("ticketTypeId");


--
-- Name: Order_code_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Order_code_key" ON public."Order" USING btree (code);


--
-- Name: Order_customerId_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Order_customerId_createdAt_idx" ON public."Order" USING btree ("customerId", "createdAt");


--
-- Name: Order_eventId_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Order_eventId_status_idx" ON public."Order" USING btree ("eventId", status);


--
-- Name: Order_status_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Order_status_createdAt_idx" ON public."Order" USING btree (status, "createdAt");


--
-- Name: ProductCategory_name_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "ProductCategory_name_key" ON public."ProductCategory" USING btree (name);


--
-- Name: Product_categoryId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Product_categoryId_idx" ON public."Product" USING btree ("categoryId");


--
-- Name: Product_sku_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Product_sku_key" ON public."Product" USING btree (sku);


--
-- Name: Promoter_code_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Promoter_code_key" ON public."Promoter" USING btree (code);


--
-- Name: Promoter_userId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Promoter_userId_key" ON public."Promoter" USING btree ("userId");


--
-- Name: PurchaseOrderItem_productId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "PurchaseOrderItem_productId_idx" ON public."PurchaseOrderItem" USING btree ("productId");


--
-- Name: PurchaseOrder_status_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "PurchaseOrder_status_createdAt_idx" ON public."PurchaseOrder" USING btree (status, "createdAt");


--
-- Name: PurchaseOrder_supplierId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "PurchaseOrder_supplierId_idx" ON public."PurchaseOrder" USING btree ("supplierId");


--
-- Name: Segment_name_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Segment_name_key" ON public."Segment" USING btree (name);


--
-- Name: Shift_employeeId_startsAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Shift_employeeId_startsAt_idx" ON public."Shift" USING btree ("employeeId", "startsAt");


--
-- Name: Shift_eventId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Shift_eventId_idx" ON public."Shift" USING btree ("eventId");


--
-- Name: Shift_positionId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Shift_positionId_idx" ON public."Shift" USING btree ("positionId");


--
-- Name: StaffPosition_name_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "StaffPosition_name_key" ON public."StaffPosition" USING btree (name);


--
-- Name: StockItem_productId_venueId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "StockItem_productId_venueId_key" ON public."StockItem" USING btree ("productId", "venueId");


--
-- Name: StockItem_venueId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "StockItem_venueId_idx" ON public."StockItem" USING btree ("venueId");


--
-- Name: StockMovement_productId_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "StockMovement_productId_createdAt_idx" ON public."StockMovement" USING btree ("productId", "createdAt");


--
-- Name: StockMovement_venueId_createdAt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "StockMovement_venueId_createdAt_idx" ON public."StockMovement" USING btree ("venueId", "createdAt");


--
-- Name: TableReservation_customerId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "TableReservation_customerId_idx" ON public."TableReservation" USING btree ("customerId");


--
-- Name: TableReservation_eventId_tableId_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "TableReservation_eventId_tableId_key" ON public."TableReservation" USING btree ("eventId", "tableId");


--
-- Name: TicketType_eventId_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "TicketType_eventId_active_idx" ON public."TicketType" USING btree ("eventId", active);


--
-- Name: Ticket_code_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Ticket_code_key" ON public."Ticket" USING btree (code);


--
-- Name: Ticket_eventId_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "Ticket_eventId_status_idx" ON public."Ticket" USING btree ("eventId", status);


--
-- Name: TimeClock_employeeId_clockIn_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "TimeClock_employeeId_clockIn_idx" ON public."TimeClock" USING btree ("employeeId", "clockIn");


--
-- Name: TimeClock_shiftId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "TimeClock_shiftId_idx" ON public."TimeClock" USING btree ("shiftId");


--
-- Name: User_email_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "User_email_key" ON public."User" USING btree (email);


--
-- Name: VipTable_zoneId_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "VipTable_zoneId_idx" ON public."VipTable" USING btree ("zoneId");


--
-- Name: Zone_venueId_name_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "Zone_venueId_name_key" ON public."Zone" USING btree ("venueId", name);


--
-- Name: AgentProposal AgentProposal_agentRunId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."AgentProposal"
    ADD CONSTRAINT "AgentProposal_agentRunId_fkey" FOREIGN KEY ("agentRunId") REFERENCES public."AgentRun"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: AgentProposal AgentProposal_reviewedById_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."AgentProposal"
    ADD CONSTRAINT "AgentProposal_reviewedById_fkey" FOREIGN KEY ("reviewedById") REFERENCES public."User"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: AgentRun AgentRun_triggeredById_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."AgentRun"
    ADD CONSTRAINT "AgentRun_triggeredById_fkey" FOREIGN KEY ("triggeredById") REFERENCES public."User"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: BarSaleItem BarSaleItem_barSaleId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."BarSaleItem"
    ADD CONSTRAINT "BarSaleItem_barSaleId_fkey" FOREIGN KEY ("barSaleId") REFERENCES public."BarSale"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: BarSaleItem BarSaleItem_productId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."BarSaleItem"
    ADD CONSTRAINT "BarSaleItem_productId_fkey" FOREIGN KEY ("productId") REFERENCES public."Product"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: BarSale BarSale_cashSessionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."BarSale"
    ADD CONSTRAINT "BarSale_cashSessionId_fkey" FOREIGN KEY ("cashSessionId") REFERENCES public."CashSession"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: BarSale BarSale_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."BarSale"
    ADD CONSTRAINT "BarSale_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Campaign Campaign_agentRunId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Campaign"
    ADD CONSTRAINT "Campaign_agentRunId_fkey" FOREIGN KEY ("agentRunId") REFERENCES public."AgentRun"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Campaign Campaign_segmentId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Campaign"
    ADD CONSTRAINT "Campaign_segmentId_fkey" FOREIGN KEY ("segmentId") REFERENCES public."Segment"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: CashSession CashSession_employeeId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CashSession"
    ADD CONSTRAINT "CashSession_employeeId_fkey" FOREIGN KEY ("employeeId") REFERENCES public."Employee"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: CashSession CashSession_venueId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CashSession"
    ADD CONSTRAINT "CashSession_venueId_fkey" FOREIGN KEY ("venueId") REFERENCES public."Venue"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ChatMessage ChatMessage_agentRunId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ChatMessage"
    ADD CONSTRAINT "ChatMessage_agentRunId_fkey" FOREIGN KEY ("agentRunId") REFERENCES public."AgentRun"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: ChatMessage ChatMessage_sessionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ChatMessage"
    ADD CONSTRAINT "ChatMessage_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES public."ChatSession"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: ChatSession ChatSession_customerId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."ChatSession"
    ADD CONSTRAINT "ChatSession_customerId_fkey" FOREIGN KEY ("customerId") REFERENCES public."Customer"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: CheckIn CheckIn_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CheckIn"
    ADD CONSTRAINT "CheckIn_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: CheckIn CheckIn_gateId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CheckIn"
    ADD CONSTRAINT "CheckIn_gateId_fkey" FOREIGN KEY ("gateId") REFERENCES public."Gate"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: CheckIn CheckIn_scannedById_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CheckIn"
    ADD CONSTRAINT "CheckIn_scannedById_fkey" FOREIGN KEY ("scannedById") REFERENCES public."User"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: CheckIn CheckIn_ticketId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CheckIn"
    ADD CONSTRAINT "CheckIn_ticketId_fkey" FOREIGN KEY ("ticketId") REFERENCES public."Ticket"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: CustomerSegment CustomerSegment_customerId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CustomerSegment"
    ADD CONSTRAINT "CustomerSegment_customerId_fkey" FOREIGN KEY ("customerId") REFERENCES public."Customer"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: CustomerSegment CustomerSegment_segmentId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."CustomerSegment"
    ADD CONSTRAINT "CustomerSegment_segmentId_fkey" FOREIGN KEY ("segmentId") REFERENCES public."Segment"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Customer Customer_userId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Customer"
    ADD CONSTRAINT "Customer_userId_fkey" FOREIGN KEY ("userId") REFERENCES public."User"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Employee Employee_positionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Employee"
    ADD CONSTRAINT "Employee_positionId_fkey" FOREIGN KEY ("positionId") REFERENCES public."StaffPosition"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Employee Employee_userId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Employee"
    ADD CONSTRAINT "Employee_userId_fkey" FOREIGN KEY ("userId") REFERENCES public."User"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: EventArtist EventArtist_artistId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EventArtist"
    ADD CONSTRAINT "EventArtist_artistId_fkey" FOREIGN KEY ("artistId") REFERENCES public."Artist"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: EventArtist EventArtist_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."EventArtist"
    ADD CONSTRAINT "EventArtist_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Event Event_venueId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Event"
    ADD CONSTRAINT "Event_venueId_fkey" FOREIGN KEY ("venueId") REFERENCES public."Venue"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Expense Expense_categoryId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Expense"
    ADD CONSTRAINT "Expense_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES public."ExpenseCategory"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Expense Expense_employeeId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Expense"
    ADD CONSTRAINT "Expense_employeeId_fkey" FOREIGN KEY ("employeeId") REFERENCES public."Employee"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Expense Expense_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Expense"
    ADD CONSTRAINT "Expense_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Gate Gate_venueId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Gate"
    ADD CONSTRAINT "Gate_venueId_fkey" FOREIGN KEY ("venueId") REFERENCES public."Venue"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: GuestListEntry GuestListEntry_guestListId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."GuestListEntry"
    ADD CONSTRAINT "GuestListEntry_guestListId_fkey" FOREIGN KEY ("guestListId") REFERENCES public."GuestList"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: GuestList GuestList_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."GuestList"
    ADD CONSTRAINT "GuestList_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: GuestList GuestList_promoterId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."GuestList"
    ADD CONSTRAINT "GuestList_promoterId_fkey" FOREIGN KEY ("promoterId") REFERENCES public."Promoter"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Interaction Interaction_customerId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Interaction"
    ADD CONSTRAINT "Interaction_customerId_fkey" FOREIGN KEY ("customerId") REFERENCES public."Customer"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: LoyaltyLedger LoyaltyLedger_customerId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LoyaltyLedger"
    ADD CONSTRAINT "LoyaltyLedger_customerId_fkey" FOREIGN KEY ("customerId") REFERENCES public."Customer"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: LoyaltyLedger LoyaltyLedger_orderId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."LoyaltyLedger"
    ADD CONSTRAINT "LoyaltyLedger_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES public."Order"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: OrderItem OrderItem_orderId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."OrderItem"
    ADD CONSTRAINT "OrderItem_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES public."Order"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: OrderItem OrderItem_ticketTypeId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."OrderItem"
    ADD CONSTRAINT "OrderItem_ticketTypeId_fkey" FOREIGN KEY ("ticketTypeId") REFERENCES public."TicketType"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Order Order_customerId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Order"
    ADD CONSTRAINT "Order_customerId_fkey" FOREIGN KEY ("customerId") REFERENCES public."Customer"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Order Order_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Order"
    ADD CONSTRAINT "Order_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Order Order_promoterId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Order"
    ADD CONSTRAINT "Order_promoterId_fkey" FOREIGN KEY ("promoterId") REFERENCES public."Promoter"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Product Product_categoryId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Product"
    ADD CONSTRAINT "Product_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES public."ProductCategory"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Promoter Promoter_userId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Promoter"
    ADD CONSTRAINT "Promoter_userId_fkey" FOREIGN KEY ("userId") REFERENCES public."User"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: PurchaseOrderItem PurchaseOrderItem_productId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PurchaseOrderItem"
    ADD CONSTRAINT "PurchaseOrderItem_productId_fkey" FOREIGN KEY ("productId") REFERENCES public."Product"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: PurchaseOrderItem PurchaseOrderItem_purchaseOrderId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PurchaseOrderItem"
    ADD CONSTRAINT "PurchaseOrderItem_purchaseOrderId_fkey" FOREIGN KEY ("purchaseOrderId") REFERENCES public."PurchaseOrder"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: PurchaseOrder PurchaseOrder_supplierId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."PurchaseOrder"
    ADD CONSTRAINT "PurchaseOrder_supplierId_fkey" FOREIGN KEY ("supplierId") REFERENCES public."Supplier"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Shift Shift_employeeId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Shift"
    ADD CONSTRAINT "Shift_employeeId_fkey" FOREIGN KEY ("employeeId") REFERENCES public."Employee"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Shift Shift_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Shift"
    ADD CONSTRAINT "Shift_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Shift Shift_positionId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Shift"
    ADD CONSTRAINT "Shift_positionId_fkey" FOREIGN KEY ("positionId") REFERENCES public."StaffPosition"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: StockItem StockItem_productId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StockItem"
    ADD CONSTRAINT "StockItem_productId_fkey" FOREIGN KEY ("productId") REFERENCES public."Product"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: StockItem StockItem_venueId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StockItem"
    ADD CONSTRAINT "StockItem_venueId_fkey" FOREIGN KEY ("venueId") REFERENCES public."Venue"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: StockMovement StockMovement_employeeId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StockMovement"
    ADD CONSTRAINT "StockMovement_employeeId_fkey" FOREIGN KEY ("employeeId") REFERENCES public."Employee"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: StockMovement StockMovement_productId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StockMovement"
    ADD CONSTRAINT "StockMovement_productId_fkey" FOREIGN KEY ("productId") REFERENCES public."Product"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: StockMovement StockMovement_venueId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."StockMovement"
    ADD CONSTRAINT "StockMovement_venueId_fkey" FOREIGN KEY ("venueId") REFERENCES public."Venue"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: TableReservation TableReservation_customerId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TableReservation"
    ADD CONSTRAINT "TableReservation_customerId_fkey" FOREIGN KEY ("customerId") REFERENCES public."Customer"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: TableReservation TableReservation_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TableReservation"
    ADD CONSTRAINT "TableReservation_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: TableReservation TableReservation_tableId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TableReservation"
    ADD CONSTRAINT "TableReservation_tableId_fkey" FOREIGN KEY ("tableId") REFERENCES public."VipTable"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: TicketType TicketType_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TicketType"
    ADD CONSTRAINT "TicketType_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Ticket Ticket_eventId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Ticket"
    ADD CONSTRAINT "Ticket_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES public."Event"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Ticket Ticket_orderId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Ticket"
    ADD CONSTRAINT "Ticket_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES public."Order"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: Ticket Ticket_ticketTypeId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Ticket"
    ADD CONSTRAINT "Ticket_ticketTypeId_fkey" FOREIGN KEY ("ticketTypeId") REFERENCES public."TicketType"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: TimeClock TimeClock_employeeId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TimeClock"
    ADD CONSTRAINT "TimeClock_employeeId_fkey" FOREIGN KEY ("employeeId") REFERENCES public."Employee"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: TimeClock TimeClock_shiftId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."TimeClock"
    ADD CONSTRAINT "TimeClock_shiftId_fkey" FOREIGN KEY ("shiftId") REFERENCES public."Shift"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: VipTable VipTable_venueId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."VipTable"
    ADD CONSTRAINT "VipTable_venueId_fkey" FOREIGN KEY ("venueId") REFERENCES public."Venue"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: VipTable VipTable_zoneId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."VipTable"
    ADD CONSTRAINT "VipTable_zoneId_fkey" FOREIGN KEY ("zoneId") REFERENCES public."Zone"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Zone Zone_venueId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."Zone"
    ADD CONSTRAINT "Zone_venueId_fkey" FOREIGN KEY ("venueId") REFERENCES public."Venue"(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

