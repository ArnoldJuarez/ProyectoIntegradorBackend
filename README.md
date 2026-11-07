# Ssiomama — backend

API REST en Java con Spring Boot. Sirve la web pública (cartelera, compra de
entradas, boxes, boletín y el chat del conserje), el panel interno (eventos,
pedidos, clientes, CRM, promotores, caja, inventario, el plano del local y los
agentes de IA) y las dos pantallas de turno (puerta y barra).

El frontend que la consume vive en **otro repositorio**: `ssiomama-frontend`.

```
Spring Boot 3.5.3 · Java 21 · PostgreSQL 18 · Flyway · 365 tests
```

## Qué hace falta instalado

| Pieza | Versión | Cómo se comprueba |
|---|---|---|
| JDK | 21 o superior | `javac -version` |
| PostgreSQL | 18, en el puerto 5432 | `Get-Service postgresql-x64-18` |

**Maven no se instala.** El proyecto trae el wrapper: usa `./mvnw` (o `mvnw.cmd`
en PowerShell) y él se descarga la versión correcta la primera vez.

## La primera vez, en cada máquina

Hay **un archivo que no está en git y sin el cual el backend no arranca**. Es a
propósito: lleva la contraseña de la base y las claves que firman los QR y las
sesiones, y eso no se versiona nunca.

Crea `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    password: TU_PASSWORD_DE_POSTGRES

ssiomama:
  ticket-secret: "el_TICKET_SECRET_del_proyecto_anterior"
  auth-secret: "el_AUTH_SECRET_del_proyecto_anterior"
```

> **Los dos secretos tienen que ser los mismos en todas las máquinas del
> equipo.** `ticket-secret` firma los códigos QR: con otro valor, las entradas
> que ya están vendidas **no validan en la puerta**. La migración V7 (ids
> numéricos) vuelve a firmar todas las entradas con el secreto de la máquina
> donde se ejecuta, así que tiene que estar puesto antes del primer arranque. `auth-secret`
> firma la cookie de sesión: al cambiarlo, todo el personal tiene que volver a
> entrar.
>
> Pídeselos a quien tenga el `.env` del proyecto anterior. No los mandéis por
> un canal público.

Sin ese archivo el backend falla al arrancar y dice cuál falta. Mejor eso que
arrancar con un valor por defecto equivocado.

### La IA

Los agentes y el conserje del chat usan **Groq**, el mismo proveedor que el
proyecto anterior, por su API compatible con OpenAI. Solo hace falta la clave,
en `application-local.yml` (es la `AI_API_KEY` del `.env` del proyecto
anterior):

```yaml
ssiomama:
  ia:
    api-key: "la_AI_API_KEY_del_proyecto_anterior"
```

Sin clave el backend arranca igual, avisa en el log y la IA queda apagada: el
chat contesta con el teléfono del local y el panel dice qué falta. Para cambiar
de proveedor o de modelo (Gemini, OpenRouter) basta con `base-url` y `modelo`,
o las variables `AI_BASE_URL`, `AI_MODEL`, `AI_API_KEY`, y `CHAT_*` si el
conserje debe ir a otro motor.

## Arrancar

```bash
./mvnw spring-boot:run
```

Queda en <http://localhost:8080>. El primer arranque tarda unos 25 segundos.

Para comprobar que va, sin abrir el navegador:

```bash
curl http://localhost:8080/api/publico/agenda
```

## La base de datos

Usa la base **`discoteca`**, la misma que el sistema anterior, con sus datos
reales dentro. Lo que cambió es quién manda en el esquema: ahora **Flyway**, no
Prisma.

Las migraciones están en `src/main/resources/db/migration/` y se aplican solas
al arrancar:

- `V1__esquema_inicial.sql` — el esquema heredado: 38 tablas, 21 tipos enum, 109
  restricciones y 74 índices. Sobre una base que ya las tiene **no se ejecuta**:
  Flyway la adopta marcándola como versión 1 (`baseline-on-migrate`). Sobre una
  base vacía sí se ejecuta y deja el esquema completo.
- Todo cambio nuevo va en `V2__...sql`, `V3__...sql`. **Nunca se edita una
  migración ya aplicada**: Flyway guarda su checksum y se niega a seguir si
  cambia.

### Empezar con una base vacía

```bash
createdb -U postgres discoteca
./mvnw spring-boot:run
```

Flyway detecta que está vacía y ejecuta `V1` entero. Quedan las tablas pero
**sin datos**: para poblarla, restaura el `backup.sql` del proyecto anterior con
`pg_restore`.

### Los datos de muestra

Para que los agentes de IA tengan con qué trabajar, la base lleva cuatro meses
de operación simulada (junio-octubre de 2026): 49 noches con las promos reales
de la casa, unos 2 100 clientes, 6 300 pedidos, 15 000 entradas firmadas que
validan en la puerta, 13 000 ventas de barra con su salida de stock, compras a
proveedores, gastos, boxes, listas de promotor y campañas.

Lo genera `datos/generar_datos.py` con las mismas reglas que el backend: los
contadores denormalizados —`vendidas`, el esperado de caja, la ficha del
cliente— cuadran con lo que los tests comprueban contra la base real. La
semilla es fija, así que volver a generarlo da exactamente lo mismo.

Se construye **encima de la base de antes**, que quedó guardada en
`datos/respaldo-discoteca-2026-10-04.dump`. Aplicado dos veces duplicaría
noches y ventas, así que si la base ya tiene los datos el generador se niega.
Para regenerarlos desde cero:

```bash
pg_restore -U postgres -d discoteca --clean --if-exists datos/respaldo-discoteca-2026-10-04.dump
python datos/generar_datos.py
psql -U postgres -d discoteca -1 -v ON_ERROR_STOP=1 -f datos/poblar.sql
```

Una **segunda tanda**, `datos/generar_noviembre.py`, pone la base al día a 4 de
noviembre de 2026: celebra y cierra las siete noches de octubre que quedaron a
la venta (accesos, venta en puerta, barra con su caja, turnos, gastos e
incidencias), repone el almacén, añade tres productos y programa noviembre y
diciembre hasta Año Nuevo con su preventa. Deja jueves libres y algún producto
corto a propósito, para que el programador y el agente de inventario tengan algo
que proponer. Va después de la primera y también se niega si ya se aplicó:

```bash
python datos/generar_noviembre.py
psql -U postgres -d discoteca -1 -v ON_ERROR_STOP=1 -f datos/poblar-noviembre.sql
```

`poblar.sql`, `poblar-noviembre.sql` y los respaldos no van a git (llevan datos
de clientes reales); los generadores sí.

### Aviso sobre el proyecto anterior

Flyway creó la tabla `flyway_schema_history`. Esa tabla **no está en el
`schema.prisma` del proyecto Next.js**, así que un `npx prisma db push` allí
puede borrarla y con ella el historial de migraciones. Ese proyecto está
congelado; si hay que tocarlo, que sea sin `db push`.

## Los tests

```bash
./mvnw test
```

Son 365, en 48 clases. Conviene saber qué protege cada grupo antes de tocarlos:

| Test | Qué protege |
|---|---|
| `FirmaDeEntradaTest` | Que el HMAC del QR salga **idéntico** al de Node. Si falla, las entradas vendidas no validan en la puerta. |
| `ScryptDeNodeEncoderTest` | Que las contraseñas hasheadas por Node se sigan verificando. Si falla, nadie del personal entra. |
| `TokenDeSesionTest` | Que la cookie de sesión sea byte a byte la del sistema anterior. |
| `PermisosServidorRealTest` | Los códigos 401 / 403 con **Tomcat de verdad**. |
| `EventoRepositoryTest` | Que las fechas no se corran de zona horaria. |
| `CarteleraTest` | Que la web pública **no** filtre lo recaudado, las ventas ni los borradores. |
| `BoletinTest` / `ReservadosTest` | Las escrituras públicas. Limpian lo que ensucian. |
| `ClienteControllerTest` | Que los datos personales estén cerrados y no salga ningún hash. |
| `ProductoRepositoryTest` | Que las entidades JPA cuadren con el esquema real. |
| `EsquemaJsonTest` | Que una respuesta mal formada de la IA no pase como propuesta válida. |
| `ModeloDeDemandaTest` | Que la regresión del agente de inventario recupere efectos conocidos. |

> **Los tests escriben en `discoteca`, la base de verdad.** Los que crean filas
> usan correos bajo `@ejemplo.test` —dominio reservado por la RFC 2606— con un
> prefijo por clase (`boletin-`, `reserva-`), y cada una borra **solo lo suyo**
> en un `@AfterAll`. Lo correcto sería una base aparte para tests; mientras no
> la haya, limpiar es el mínimo aceptable.

## Cuatro cosas que cuesta descubrir solo

Están explicadas en el código, pero conviene tenerlas a mano:

**Spring Initializr ofrece versiones que no existen en Maven Central.** Daba
`4.1.1.RELEASE` y el build no resuelve el POM padre. Verifica contra
`search.maven.org` antes de fijar una versión.

**Spring Boot pisa tus `@Table` y `@Column`.** Su estrategia de nombres por
defecto los reescribe a snake_case en minúsculas, y contra un esquema en
camelCase no encuentra nada. Por eso `application.yml` fija
`PhysicalNamingStrategyStandardImpl` y `globally_quoted_identifiers`.

**Las fechas se corren cinco horas sin dar ningún error.** Las columnas son
`timestamp without time zone` y guardan el instante en UTC. Sin
`hibernate.jdbc.time_zone: UTC`, Hibernate las interpreta en la zona del sistema
operativo. `EventoRepositoryTest` comprueba un instante concreto por eso.

**Al filtrar por un enum, nunca pongas el literal en el HQL.** Hibernate genera
el cast con el nombre de la clase Java (`::EstadoDeEvento`) y el tipo en la base
se llama `EstadoEvento`. Los valores van siempre como parámetro.

## Estado

**Migrado entero** desde el sistema en Next.js. Lo último en llegar:

- **Promotores** (`/api/promotores`): listas de invitados y comisión calculada.
- **Flyer de la noche** (`/api/eventos/{slug}/portada`): se sube, se reescala y
  lo sirve el propio backend en `/api/publico/portadas/...`. Se guarda en la
  carpeta `portadas/` (fuera de git; se cambia con `CARPETA_PORTADAS`).
- **El local** (`/api/local`): medidas, zonas y puertas de cada sala. La
  evacuación se calcula en el navegador.
- **IA** (`/api/ia/**` y `/api/publico/chat`): siete agentes que dejan
  propuestas, la aprobación humana que las aplica, métricas y el conserje.
  Ningún agente toca datos sin que alguien pulse «Aprobar». El séptimo, el
  **programador de noches**, propone noches completas que se pueden retocar
  antes de aprobar y se crean en borrador.

### Gestión a mano

Todo lo que propone la IA se puede hacer también a mano:

- **Video de la noche** (`/api/eventos/{slug}/video`): MP4/WebM hasta 80 MB,
  comprobado por su cabecera y servido por rangos; o un enlace https.
- **Borrar noches** sin historia (`DELETE /api/eventos/{slug}`).
- **Artistas** (`/api/artistas`): catálogo con foto.
- **Pedidos a proveedor y proveedores** (`/api/inventario/ordenes`,
  `/proveedores`): recibir un pedido suma el stock.
- **Reservas de box** (`/api/reservas`), **gastos** (`/api/gastos`),
  **listas de invitados** (`/api/promotores/listas`) y **personal**
  (`/api/personal`, solo dirección).

La migración `V2` añade la columna del video y los valores de enum del
programador.

### Documentación

El vault de Obsidian completo (requisitos, casos de uso, arquitectura, UML,
BPMN, diccionario de datos, API y una nota por clase) está en
`../documentacion`. La parte que sale del código se regenera con
`python ../documentacion/_herramientas/generar.py`.

Las tablas de la IA se escriben con SQL a mano (`RegistroDeIa`) y no con JPA:
sus enum tienen etiquetas con guiones (`crm-segmenter`) que Hibernate no sabe
convertir. Está explicado en la propia clase.

**No se migraron** los scripts de línea de comandos del proyecto anterior
(`npm run benchmark`, `historico`, `proyeccion`, `consumo`, `evacuacion`...):
eran herramientas de la tesis, no parte de la aplicación.
