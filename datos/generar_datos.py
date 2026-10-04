"""
Genera cuatro meses de operación de Ssiomama (junio-octubre 2026) y los deja en
`poblar.sql`, listo para aplicar en una sola transacción.

Lo que simula, con las mismas reglas que el backend:

- Noches de jueves a domingo con las promos reales de la casa (barra libre para
  mujeres, sellada de Cartavio, jarra de sangría, box por cumpleaños).
- Pedidos online y en puerta, entradas firmadas con el TICKET_SECRET real (validan
  en la puerta), accesos escaneados y quien no llegó a venir.
- La ficha del cliente como la deja `Compra.confirmarPago`: visitas, gasto, última
  visita, puntos de fidelidad e interacción de compra.
- Barra: una caja por noche, ventas con sus líneas, salida de stock por cada venta,
  arqueo con su descuadre real. Reposición semanal a proveedores.
- Gastos, reservas de box, listas de promotor y campañas.

La aleatoriedad tiene semilla fija: volver a generar da exactamente lo mismo.

Uso:  python generar_datos.py      (escribe poblar.sql al lado)

Se aplica una sola vez, sobre la base del respaldo: si ya tiene los datos, se niega.
"""

import base64
import datetime as dt
import hashlib
import hmac
import math
import os
import random
import re
import subprocess
import unicodedata
import uuid
from collections import defaultdict

R = random.Random(20260605)

RAIZ = os.path.dirname(os.path.abspath(__file__))
LOCAL_YML = os.path.join(RAIZ, "..", "src", "main", "resources", "application-local.yml")
PSQL = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"
HOY = dt.date(2026, 10, 4)  # domingo: lo último que pasó fue el sábado 3


def leer_yml(clave):
    for linea in open(LOCAL_YML, encoding="utf-8"):
        m = re.match(rf'\s*{clave}:\s*"?([^"\n]*?)"?\s*$', linea)
        if m:
            return m.group(1)
    raise SystemExit(f"Falta {clave} en application-local.yml")


ENV = dict(os.environ, PGPASSWORD=leer_yml("password"), PGCLIENTENCODING="UTF8")
SECRETO = leer_yml("ticket-secret").encode()


def consulta(sql):
    r = subprocess.run(
        [PSQL, "-U", "postgres", "-d", "discoteca", "-At", "-F", "\x1f", "-c", sql],
        capture_output=True, env=ENV, check=True)
    texto = r.stdout.decode("utf-8")
    return [linea.split("\x1f") for linea in texto.splitlines() if linea]


# ─────────────────────────── utilidades ───────────────────────────

def nuevo_id():
    return str(uuid.uuid4())


def lima(fecha, horas, minutos=0, segundos=0):
    """Hora de la sala (UTC-5, sin horario de verano) a UTC sin zona, como guarda la base.
    `horas` puede pasar de 24 para la madrugada siguiente."""
    base = dt.datetime(fecha.year, fecha.month, fecha.day)
    return base + dt.timedelta(hours=horas + 5, minutes=minutos, seconds=segundos)


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, dt.datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S.") + f"{v.microsecond // 1000:03d}'"
    if isinstance(v, dt.date):
        return "'" + v.isoformat() + "'"
    return "'" + str(v).replace("'", "''") + "'"


def sin_tildes(s):
    s = s.replace("ñ", "n").replace("Ñ", "N")
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def elegir(pares):
    """[(valor, peso), ...] -> valor."""
    total = sum(p for _, p in pares)
    x = R.uniform(0, total)
    for valor, peso in pares:
        x -= peso
        if x <= 0:
            return valor
    return pares[-1][0]


def normal_acotada(media, desv, minimo, maximo):
    while True:
        v = R.gauss(media, desv)
        if minimo <= v <= maximo:
            return v


def firma(id_entrada):
    mac = hmac.new(SECRETO, id_entrada.encode(), hashlib.sha256).digest()
    return base64.urlsafe_b64encode(mac).decode().rstrip("=")[:24]


ALFABETO = "ABCDEFGHJKLMNPQRTUVWXYZ2346789"


class Salida:
    """Acumula filas por tabla y las escribe en INSERT por lotes."""

    def __init__(self):
        self.filas = defaultdict(list)
        self.columnas = {}
        self.orden = []
        self.sentencias = []

    def fila(self, tabla, **valores):
        if tabla not in self.columnas:
            self.columnas[tabla] = list(valores)
            self.orden.append(tabla)
        self.filas[tabla].append(valores)

    def sql(self, sentencia):
        self.sentencias.append(sentencia)

    def escribir(self, ruta, orden_tablas):
        with open(ruta, "w", encoding="utf-8") as f:
            f.write("-- Generado por generar_datos.py. Aplicar con: psql -1 -v ON_ERROR_STOP=1 -f poblar.sql\n")
            f.write("SET client_encoding = 'UTF8';\n\n")
            f.write("-- Arreglos sobre los datos que ya había\n")
            for s in self.sentencias:
                f.write(s.rstrip(";") + ";\n")
            for tabla in orden_tablas:
                filas = self.filas.get(tabla, [])
                if not filas:
                    continue
                cols = self.columnas[tabla]
                f.write(f"\n-- {tabla}: {len(filas)} filas\n")
                for i in range(0, len(filas), 400):
                    lote = filas[i:i + 400]
                    f.write(f'INSERT INTO "{tabla}" (' + ", ".join(f'"{c}"' for c in cols) + ") VALUES\n")
                    f.write(",\n".join("(" + ", ".join(q(fila[c]) for c in cols) + ")" for fila in lote))
                    f.write(";\n")


S = Salida()

# ─────────────────────────── lo que ya hay ───────────────────────────

SALA_ID, AFORO = consulta('select id, aforo from "Sala" order by aforo desc limit 1')[0]
AFORO = int(AFORO)
PUERTA_PRINCIPAL = consulta("""select id from "Puerta" where not "esSalidaEmergencia" order by nombre limit 1""")[0][0]
USUARIOS = {correo or nombre: uid for uid, correo, nombre in consulta('select id, correo, nombre from "Usuario"')}
GERENTE = USUARIOS["sala@ssiomama.test"]
DIRECTOR = USUARIOS["admin@ssiomama.test"]
PORTERO = USUARIOS["puerta@ssiomama.test"]
BARTENDER = USUARIOS["barra@ssiomama.test"]
PIERO = USUARIOS["Piero Lazo"]
PUESTOS = {nombre: pid for pid, nombre in consulta('select id, nombre from "Puesto"')}
CATEGORIAS = {nombre: cid for cid, nombre in consulta('select id, nombre from "CategoriaProducto"')}
CAT_GASTO = {nombre: cid for cid, nombre in consulta('select id, nombre from "CategoriaGasto"')}
SEGMENTOS = {nombre: sid for sid, nombre in consulta('select id, nombre from "Segmento"')}
BOXES = [dict(id=b, nombre=n, zona=z, plazas=int(p), consumo=int(c)) for b, n, z, p, c in consulta(
    'select b.id, b.nombre, z.nombre, b.plazas, b."consumoMinimoCentimos" from "Box" b '
    'left join "Zona" z on z.id = b."zonaId" where b.activo order by b.nombre')]
RESERVAS_EXISTENTES = {(e, b) for e, b in consulta('select "eventoId", "boxId" from "ReservaBox"')}
CODIGOS = {c for (c,) in consulta('select codigo from "Pedido"')}
CORREOS = {c.lower() for (c,) in consulta('select correo from "Cliente"')}
ARTISTAS = {n: aid for aid, n in consulta('select id, nombre from "Artista"')}
PROVEEDOR_EXISTENTE = consulta('select id from "Proveedor" limit 1')[0][0]
SLUGS = {s for (s,) in consulta('select slug from "Evento"')}

# Se construye encima de la base de antes (la del respaldo). Aplicado sobre sí
# mismo duplicaría noches, clientes y ventas, así que se niega.
if consulta("""select 1 from "Proveedor" where nombre = 'Distribuidora Santa Catalina S.A.C.'"""):
    raise SystemExit("La base ya tiene los datos de muestra. Para regenerarlos, restaura antes "
                     "respaldo-discoteca-2026-10-04.dump (ver el README del backend).")

PRODUCTOS = {}
for pid, sku, nombre, coste, precio, stock, alerta in consulta(
        'select id, sku, nombre, "costeCentimos", "precioCentimos", stock, "alertaStock" from "Producto"'):
    PRODUCTOS[sku] = dict(id=pid, sku=sku, nombre=nombre, coste=int(coste), precio=int(precio),
                          stock=int(stock), alerta=int(alerta), nuevo=False)

# ─────────────────────────── nombres de verdad ───────────────────────────

MUJERES = ("Valeria Camila Fernanda Alessandra Daniela Andrea Milagros Lucero Kiara Brigitte Nicole Ariana "
           "Xiomara Yamileth Fiorella Karla Gianella Thalía Dayana Estefany Shirley Mayra Ximena Antonella "
           "Lizbeth Rocío Karen Diana Claudia Gabriela Melany Nayeli Jazmín Allison Abigail Luana Briggitte "
           "Rosmery Yesenia Sheyla Mariela Katherine Pamela Sofía Valentina Mía Danitza Analí Luz Maricielo "
           "Flor Angie Joselyn Massiel Ruth Nathaly Kassandra Zarela Fabiola Romina Janeth Leslie Vania").split()
HOMBRES = ("Diego Luis Jhon Jhonatan Kevin Brayan Renzo Sebastián Rodrigo Jorge Fabrizio Gonzalo Joel Anthony "
           "Christian Alonso Ángel Marco Frank Erick Josué Alexis Paolo Gustavo Miguel Jean Carlos Ronald Juan "
           "Bryan Eduardo Franco Mauricio Rafael Álvaro Gerson Elvis Hugo Jesús Omar Piero Joaquín Andrés Manuel "
           "Ricardo Víctor Walter Yordy Cristhian Edson Jair Aldair Rubén Martín Fernando Nilton Henry Danilo").split()
COMPUESTOS_H = ["Jean Pierre", "Juan Carlos", "José Luis", "Luis Fernando", "Carlos Daniel", "Juan Diego"]
COMPUESTOS_M = ["María José", "Ana Lucía", "María Fernanda", "Rosa María", "Ana Paula"]
APELLIDOS = [  # con el peso aproximado que tienen en Arequipa
    ("Quispe", 9), ("Mamani", 9), ("Condori", 7), ("Huamaní", 4), ("Flores", 6), ("Rodríguez", 4), ("Chávez", 4),
    ("Zegarra", 4), ("Valdivia", 5), ("Delgado", 4), ("Pinto", 3), ("Salas", 4), ("Núñez", 3), ("Paredes", 4),
    ("Medina", 4), ("Begazo", 2), ("Bustamante", 2), ("Cornejo", 3), ("Llerena", 2), ("Manrique", 3),
    ("Rivera", 3), ("Villanueva", 2), ("Torres", 4), ("Apaza", 5), ("Ccama", 3), ("Choque", 4), ("Huanca", 4),
    ("Cutipa", 2), ("Ticona", 3), ("Cáceres", 3), ("Gutiérrez", 3), ("Zúñiga", 2), ("Tejada", 2), ("Carpio", 2),
    ("Linares", 2), ("Málaga", 2), ("Pacheco", 3), ("Talavera", 2), ("Vargas", 4), ("Rojas", 4), ("Gonzales", 4),
    ("Pérez", 3), ("Ramos", 3), ("Mendoza", 3), ("Herrera", 2), ("Benavente", 2), ("Arce", 2), ("Ponce", 2),
    ("Jiménez", 2), ("Fernández", 3), ("Coaquira", 2), ("Ccahua", 2), ("Yucra", 2), ("Ayque", 1), ("Puma", 2),
    ("Taco", 1), ("Lazo", 2), ("Peralta", 2), ("Rivas", 2), ("Barreda", 1), ("Muñoz", 2), ("Alvarez", 2),
    ("Cruz", 2), ("Romero", 2), ("Soto", 2), ("Olazábal", 1), ("Postigo", 1), ("Díaz", 3), ("Salazar", 2),
]
DISTRITOS = [("José Luis Bustamante y Rivero", 18), ("Cercado", 12), ("Cayma", 11), ("Yanahuara", 8),
             ("Cerro Colorado", 10), ("Paucarpata", 9), ("Sachaca", 5), ("Miraflores", 6), ("Mariano Melgar", 5),
             ("Socabaya", 6), ("Jacobo Hunter", 4), ("Alto Selva Alegre", 5), ("Tiabaya", 2), ("Sabandía", 1),
             ("Characato", 1), (None, 14)]
DOMINIOS = [("gmail.com", 70), ("hotmail.com", 14), ("outlook.com", 5), ("unsa.edu.pe", 4),
            ("ucsm.edu.pe", 3), ("ucsp.edu.pe", 2), ("yahoo.com", 2)]


def apellido():
    return elegir(APELLIDOS)


def correo_para(nombre, ap1, ap2, nacimiento):
    n = sin_tildes(nombre.split()[0]).lower()
    a1 = sin_tildes(ap1).lower().replace(" ", "")
    a2 = sin_tildes(ap2).lower().replace(" ", "")
    anio = str(nacimiento.year)[2:] if nacimiento else R.choice(["98", "99", "00", "01", "02", "03", "04", "05"])
    dominio = elegir(DOMINIOS)
    if dominio.endswith(".edu.pe"):
        candidatos = [f"{n}.{a1}", f"{n[0]}{a1}{a2[0]}", f"{n}.{a1}.{a2}"]
    else:
        candidatos = [f"{n}.{a1}{anio}", f"{n}{a1}{R.randint(1, 99)}", f"{n}_{a1}", f"{n[0]}{a1}{a2[:3]}",
                      f"{n}{a1[:4]}{anio}", f"{a1}.{n}", f"{n}.{a1}.{a2[0]}", f"{n}{R.choice(['xd', 'aqp', '_', '.'])}{a1}"]
    base = R.choice(candidatos).strip("._")
    correo = f"{base}@{dominio}"
    while correo.lower() in CORREOS:
        correo = f"{base}{R.randint(2, 99)}@{dominio}"
    CORREOS.add(correo.lower())
    return correo


def telefono():
    return R.choice(["95", "97", "98", "99", "94", "93", "92", "95", "97", "98"]) + "".join(
        str(R.randint(0, 9)) for _ in range(7))


def nacimiento():
    if R.random() < 0.28:
        return None
    edad = int(normal_acotada(24, 4.2, 18.2, 38))
    inicio = dt.date(HOY.year - edad - 1, HOY.month, HOY.day) + dt.timedelta(days=1)
    return inicio + dt.timedelta(days=R.randint(0, 364))


def persona(mujer):
    if mujer:
        nombre = R.choice(COMPUESTOS_M) if R.random() < 0.06 else R.choice(MUJERES)
    else:
        nombre = R.choice(COMPUESTOS_H) if R.random() < 0.08 else R.choice(HOMBRES)
    ap1, ap2 = apellido(), apellido()
    return nombre, ap1, ap2


# ─────────────────────────── catálogo y proveedores ───────────────────────────

S.sql("""update "Proveedor" set nombre = 'Distribuidora Santa Catalina S.A.C.', contacto = 'Gonzalo Linares',
         correo = 'ventas.santacatalina@gmail.com', telefono = '959 412 087' where id = """ + q(PROVEEDOR_EXISTENTE))
PROVEEDORES = {"licores": PROVEEDOR_EXISTENTE}
for clave, nombre, contacto, correo, tel in [
    ("cerveza", "Backus - Centro de distribución Arequipa", "Ronald Apaza", "pedidos.aqp@backus.com.pe", "054 382 410"),
    ("gaseosas", "Arca Continental Lindley", "Mariela Torres", "atencion.arequipa@arcacontal.pe", "054 285 960"),
    ("licores2", "Licores del Sur E.I.R.L.", "Hugo Begazo", "licoresdelsur.aqp@hotmail.com", "958 103 774"),
]:
    pid = nuevo_id()
    PROVEEDORES[clave] = pid
    S.fila("Proveedor", id=pid, nombre=nombre, contacto=contacto, correo=correo, telefono=tel)

if "Preparados" not in CATEGORIAS:
    CATEGORIAS["Preparados"] = nuevo_id()
    S.fila("CategoriaProducto", id=CATEGORIAS["Preparados"], nombre="Preparados")

# Las marcas que la casa pone al pie de sus flyers.
NUEVOS = [
    ("CER-COR-210", "Coronita 210ml", "Cerveza", "UNIDAD", 280, 600, 48),
    ("CER-STE-330", "Stella Artois 330ml", "Cerveza", "UNIDAD", 420, 1500, 24),
    ("RON-FLO-750", "Ron Flor de Caña 4 años 750ml", "Destilados", "BOTELLA", 5200, 15000, 8),
    ("WHI-CHI-750", "Whisky Chivas Regal 12 años 750ml", "Destilados", "BOTELLA", 12500, 32000, 6),
    ("GIN-BEE-750", "Gin Beefeater 750ml", "Destilados", "BOTELLA", 7800, 22000, 6),
    ("TEQ-CUE-750", "Tequila Jose Cuervo Especial 750ml", "Destilados", "BOTELLA", 6900, 19000, 6),
    ("LIC-JAG-700", "Jägermeister 700ml", "Destilados", "BOTELLA", 7200, 20000, 6),
    ("GAS-INK-500", "Inca Kola 500ml", "Gaseosas", "UNIDAD", 200, 800, 24),
    ("GAS-GIN-355", "Ginger Ale Canada Dry 355ml", "Gaseosas", "UNIDAD", 230, 700, 24),
    ("PRE-SAN-JAR", "Jarra de sangría", "Preparados", "UNIDAD", 900, 3000, 10),
]
for sku, nombre, cat, unidad, coste, precio, alerta in NUEVOS:
    pid = nuevo_id()
    PRODUCTOS[sku] = dict(id=pid, sku=sku, nombre=nombre, coste=coste, precio=precio, stock=0, alerta=alerta,
                          nuevo=True, unidad=unidad, categoria=CATEGORIAS[cat])

PROVEEDOR_DE = {}
for sku in PRODUCTOS:
    if sku.startswith("CER-"):
        PROVEEDOR_DE[sku] = "cerveza"
    elif sku.startswith(("GAS-", "SIN-")):
        PROVEEDOR_DE[sku] = "gaseosas"
    elif sku in ("WHI-CHI-750", "GIN-BEE-750", "TEQ-CUE-750", "LIC-JAG-700", "RON-FLO-750"):
        PROVEEDOR_DE[sku] = "licores2"
    else:
        PROVEEDOR_DE[sku] = "licores"
# El caso de reposición de cada producto.
CAJA = {"CER": 24, "GAS-RBU-250": 24, "GAS": 12, "SIN": 15, "PRE": 10}


def caja_de(sku):
    if sku in CAJA:
        return CAJA[sku]
    return CAJA.get(sku[:3], 6)


# ─────────────────────────── personal sin cuenta ───────────────────────────

LIZBETH, RUBEN = nuevo_id(), nuevo_id()
S.fila("Usuario", id=LIZBETH, correo=None, hashClave=None, nombre="Lizbeth Huanca", rol="BARRA", activo=True,
       creadoEn=lima(dt.date(2026, 5, 20), 11, 4), puestoId=PUESTOS.get("Barra principal"),
       fechaAlta=dt.date(2026, 5, 22), tarifaHoraCentimos=1250, codigoPromotor=None, comisionPct=None)
S.fila("Usuario", id=RUBEN, correo=None, hashClave=None, nombre="Rubén Apaza", rol="PUERTA", activo=True,
       creadoEn=lima(dt.date(2026, 5, 20), 11, 9), puestoId=PUESTOS.get("Control de acceso"),
       fechaAlta=dt.date(2026, 5, 22), tarifaHoraCentimos=1100, codigoPromotor=None, comisionPct=None)

# ─────────────────────────── artistas ───────────────────────────

NUEVOS_ARTISTAS = [
    ("DJ Beto", "Reguetón · Crossover", "djbeto.aqp",
     "Residente de la casa. Reguetón, salsa choke y crossover; arma la noche de 10 a 12 y la cierra."),
    ("La Combinación Perfecta", "Salsa y cumbia", "lacombinacionperfecta.aqp",
     "Orquesta arequipeña de salsa y cumbia. Diez músicos en vivo."),
    ("DJ Fabri", "Electrónica · Guaracha", "fabri.djs",
     "Guaracha, aleteo y house latino. Toca en Arequipa y Cusco."),
    ("Kike Valdivia", "Crossover · Retro", "kikevaldivia.dj",
     "Del rock en español al reguetón de los 2000. Sets de tres horas."),
]
for nombre, genero, ig, bio in NUEVOS_ARTISTAS:
    aid = nuevo_id()
    ARTISTAS[nombre] = aid
    S.fila("Artista", id=aid, nombre=nombre, genero=genero, urlImagen=None, instagram=ig, bio=bio)

# Cuánto llena cada cabeza de cartel por sí solo: lo que la noche promete.
TIRON = {"DJ Beto": 0.56, "La Ruka": 0.58, "DJ Chino Vega": 0.57, "Kandela Crew": 0.40,
         "La Combinación Perfecta": 0.50, "Sonido Chicha": 0.47, "Bruno Paz": 0.41, "DJ Fabri": 0.45,
         "Nena Sur": 0.46, "Kike Valdivia": 0.44}
# Qué se bebe según lo que suena: (cerveza, botellas, sangría, energizante, agua) relativo.
PERFIL = {
    "DJ Beto": (1.0, 1.0, 1.0, 1.0, 1.0), "La Ruka": (1.0, 1.1, 1.1, 1.1, 1.0),
    "DJ Chino Vega": (1.05, 1.15, 0.9, 1.1, 1.0), "Kandela Crew": (0.75, 1.25, 1.2, 0.5, 0.9),
    "La Combinación Perfecta": (1.1, 1.2, 1.0, 0.5, 0.8), "Sonido Chicha": (1.6, 0.8, 0.6, 0.4, 0.8),
    "Bruno Paz": (0.7, 0.9, 0.7, 2.0, 1.8), "DJ Fabri": (0.8, 1.0, 0.8, 1.8, 1.5),
    "Nena Sur": (0.85, 0.8, 1.6, 0.8, 1.1), "Kike Valdivia": (1.2, 0.9, 1.0, 0.8, 1.0),
}

FOTOS = {"perreo": "/fotos/cabina.webp", "barra": "/fotos/barra.webp", "vivo": "/fotos/envivo.webp",
         "show": "/fotos/show.webp", "fiesta": "/fotos/portada.webp", "aniversario": "/fotos/aniversario.webp"}
VESTIMENTA = "Casual. No se entra con short, sandalias ni ropa deportiva."
PROMO_CASA = "Si cumples años este mes, pide tu box y tu sellada gratis. Reservas de box y listas al 986 678 040."

# ─────────────────────────── el calendario ───────────────────────────
# (fecha, título, cabeza, teloneros, plantilla de entradas, foto, descripción, extra de tirón)
D = dt.date
NOCHES = [
    (D(2026, 6, 5), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Reguetón del bueno de principio a fin con DJ Beto. Preventa a S/ 15 hasta el jueves.", 1.0),
    (D(2026, 6, 6), "Sábado de Bellaqueo", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m.: whisky, ron, vodka y pisco. " + PROMO_CASA, 1.12),
    (D(2026, 6, 12), "Noche Crossover", "Kike Valdivia", ["DJ Beto"], "regular", "fiesta",
     "Del rock en español al reguetón de los 2000. Kike Valdivia toca de 12 a 2.", 1.0),
    (D(2026, 6, 13), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock, sellada de Cartavio a S/ 100 y jarra de sangría a S/ 30.", 1.05),
    (D(2026, 6, 18), "Jueves Universitario", "DJ Beto", [], "universitario", "fiesta",
     "Entrada a S/ 10 con tu carné universitario. Trae a tu promo.", 1.0),
    (D(2026, 6, 19), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Reguetón, dembow y salsa choke. Preventa a S/ 15 hasta el jueves.", 1.0),
    (D(2026, 6, 20), "Salsa Brava con Kandela Crew", "Kandela Crew", ["DJ Beto"], "regular", "vivo",
     "Kandela Crew en vivo a las 12:30. Salsa y timba toda la noche.", 1.0),
    (D(2026, 6, 26), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Preventa a S/ 15 hasta el jueves. Sellada de Cartavio a S/ 100 en puerta.", 1.0),
    (D(2026, 6, 27), "La Fogata · San Juan + Barra libre", "DJ Beto", ["Sonido Chicha"], "especial", "fiesta",
     "Celebramos San Juan con barra libre para mujeres de 11 p. m. a 12 a. m. (whisky, ron, vodka, pisco). Desde las 9:00 p. m.",
     1.15),
    (D(2026, 7, 3), "Viernes Universitario", "DJ Beto", [], "universitario", "fiesta",
     "Fin de exámenes: entrada a S/ 10 con carné universitario hasta las 11:30 p. m.", 1.05),
    (D(2026, 7, 4), "Sábado de Bellaqueo", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m. " + PROMO_CASA, 1.12),
    (D(2026, 7, 10), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock. Sellada de Cartavio S/ 100 y jarra de sangría S/ 30 en puerta.", 1.05),
    (D(2026, 7, 11), "Barra libre para mujeres · Music by DJ Beto", "DJ Beto", [], "barra_libre", "barra",
     "Toma todo lo que puedas gratis: ron, vodka, whisky y pisco de 11 p. m. a 12 a. m. Box cumpleañero, listas y reservas al 986 678 040.",
     1.15),
    (D(2026, 7, 17), "Electro Latino", "Bruno Paz", ["DJ Beto"], "regular", "show",
     "Bruno Paz con latin house y electrónica. Abre DJ Beto.", 1.0),
    (D(2026, 7, 18), "Sábado de Bellaqueo", "DJ Beto", ["DJ Chino Vega"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m. " + PROMO_CASA, 1.1),
    (D(2026, 7, 23), "Jueves Universitario · Fin de ciclo", "DJ Beto", [], "universitario", "fiesta",
     "Cerramos el ciclo. S/ 10 con carné hasta medianoche.", 1.1),
    (D(2026, 7, 24), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Arranca el feriado largo. Preventa a S/ 15.", 1.08),
    (D(2026, 7, 25), "Noche Peruana", "La Combinación Perfecta", ["Sonido Chicha"], "especial", "vivo",
     "Salsa, cumbia y chicha en vivo para recibir Fiestas Patrias. La Combinación Perfecta a la 1 a. m.", 1.1),
    (D(2026, 7, 27), "Víspera de Fiestas Patrias", "DJ Beto", ["Sonido Chicha"], "especial", "fiesta",
     "Abrimos el lunes porque el martes es feriado. Desde las 9:00 p. m.", 1.0),
    (D(2026, 7, 31), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock. Sellada de Cartavio S/ 100 en puerta.", 1.0),
    (D(2026, 8, 1), "Ven con 5 amigas · Jarra de ron gratis", "DJ Beto", [], "barra_libre", "fiesta",
     "Ven con 5 amigas y pide una jarra de ron gratis (1 jarra cada 5 mujeres al ingreso, de 9:00 a 11:30 p. m.). "
     "Sellada de Cartavio S/ 100 y jarra de sangría S/ 30 en puerta.", 1.12),
    (D(2026, 8, 7), "Guaracha Night", "DJ Fabri", ["DJ Beto"], "regular", "show",
     "DJ Fabri con guaracha y aleteo desde la 1 a. m.", 1.0),
    (D(2026, 8, 8), "Sábado de Bellaqueo", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m. " + PROMO_CASA, 1.1),
    (D(2026, 8, 14), "Víspera del 15 · Arequipa de fiesta", "La Combinación Perfecta", ["DJ Beto"], "especial", "vivo",
     "La víspera del aniversario de la ciudad, con orquesta en vivo y DJ Beto hasta el cierre.", 1.12),
    (D(2026, 8, 15), "Aniversario de Arequipa", "DJ Beto", ["Sonido Chicha"], "especial", "fiesta",
     "476 años de la Ciudad Blanca. Preventa a S/ 20, VIP con dos consumos incluidos.", 1.22),
    (D(2026, 8, 21), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Preventa a S/ 15 hasta el jueves.", 1.0),
    (D(2026, 8, 22), "Stripper + Orquesta La Combinación Perfecta", "La Combinación Perfecta", ["DJ Beto"],
     "barra_libre", "show",
     "Show de stripper para ellas, orquesta invitada La Combinación Perfecta y barra libre para mujeres de 11 p. m. a 12 a. m.",
     1.2),
    (D(2026, 8, 28), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock.", 1.0),
    (D(2026, 9, 4), "Viernes Universitario · Inicio de ciclo", "DJ Beto", [], "universitario", "fiesta",
     "Empieza el ciclo. S/ 10 con carné hasta las 11:30 p. m.", 0.9),
    (D(2026, 9, 5), "Sábado de Bellaqueo", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m. " + PROMO_CASA, 1.08),
    (D(2026, 9, 11), "Latin Pop Night", "Nena Sur", ["DJ Beto"], "regular", "show",
     "Nena Sur en vivo con lo mejor del pop latino. Jarra de sangría a S/ 30.", 1.0),
    (D(2026, 9, 12), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock. Sellada de Cartavio S/ 100.", 1.05),
    (D(2026, 9, 19), "Sábado de Bellaqueo", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m. " + PROMO_CASA, 1.08),
    (D(2026, 9, 25), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Preventa a S/ 15 hasta el jueves.", 1.0),
    (D(2026, 10, 2), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock. Sellada de Cartavio S/ 100 y jarra de sangría S/ 30 en puerta.", 1.05),
    (D(2026, 10, 3), "Sábado de Bellaqueo · Barra libre para mujeres", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m.: whisky, ron, vodka y pisco. " + PROMO_CASA, 1.12),
]
FUTURAS = [
    (D(2026, 10, 8), "Jueves Universitario", "DJ Beto", [], "universitario", "fiesta",
     "S/ 10 con carné universitario hasta las 11:30 p. m. Trae a tu promo.", 0.17),
    (D(2026, 10, 9), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock. Sellada de Cartavio S/ 100 y jarra de sangría S/ 30 en puerta.", 0.27),
    (D(2026, 10, 10), "Sábado de Bellaqueo · Barra libre para mujeres", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m.: whisky, ron, vodka y pisco. " + PROMO_CASA, 0.40),
    (D(2026, 10, 16), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Preventa a S/ 15 hasta el jueves.", 0.09),
    (D(2026, 10, 17), "Ven con 5 amigas · Jarra de ron gratis", "DJ Beto", [], "barra_libre", "fiesta",
     "Ven con 5 amigas y pide una jarra de ron gratis (1 jarra cada 5 mujeres al ingreso, de 9:00 a 11:30 p. m.).", 0.12),
    (D(2026, 10, 24), "La Combinación Perfecta en vivo", "La Combinación Perfecta", ["DJ Beto"], "especial", "vivo",
     "Orquesta en vivo a la 1 a. m. Salsa y cumbia hasta el cierre. VIP con dos consumos.", 0.06),
    (D(2026, 10, 31), "Noche de Brujas · Halloween", "DJ Beto", ["Bruno Paz"], "halloween", "show",
     "Concurso de disfraces con premio de S/ 500 y un box para el mejor grupo. Preventa a S/ 25 hasta el 24.", 0.10),
]
BORRADORES = [
    (D(2026, 11, 7), "Sábado de Bellaqueo", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m.", 0),
]

# Tipos de entrada por plantilla: (nombre, precio, cantidad, max, consumos, clave, descripción)
PLANTILLAS = {
    "regular": [("Lista mujeres · hasta 11:30 p. m.", 0, 120, 6, 0, "lista", "Ingreso libre para mujeres con su nombre en lista hasta las 11:30 p. m."),
                ("Preventa", 1500, 110, 6, 0, "preventa", "Hasta el jueves o hasta agotar."),
                ("General", 2500, 190, 6, 0, "general", None),
                ("En puerta", 3000, 80, 6, 0, "puerta", "Se vende en la puerta la misma noche.")],
    "barra_libre": [("Lista mujeres · barra libre", 0, 170, 6, 0, "lista", "Barra libre de 11 p. m. a 12 a. m. Entrada antes de las 11:30 p. m."),
                    ("Preventa", 1500, 100, 6, 0, "preventa", "Hasta el jueves o hasta agotar."),
                    ("General", 2500, 160, 6, 0, "general", None),
                    ("En puerta", 3000, 70, 6, 0, "puerta", "Se vende en la puerta la misma noche.")],
    "universitario": [("Con carné universitario", 1000, 220, 4, 0, "preventa", "Presenta tu carné vigente en la puerta. Válida hasta las 11:30 p. m."),
                      ("General", 2000, 200, 6, 0, "general", None),
                      ("En puerta", 2500, 80, 6, 0, "puerta", "Se vende en la puerta la misma noche.")],
    "especial": [("Preventa", 2000, 120, 6, 0, "preventa", "Hasta agotar."),
                 ("General", 3000, 220, 6, 0, "general", None),
                 ("VIP con 2 consumos", 6000, 50, 4, 2, "vip", "Ingreso preferente y dos tragos de la carta."),
                 ("En puerta", 3500, 110, 6, 0, "puerta", "Se vende en la puerta la misma noche.")],
    "halloween": [("Preventa", 2500, 150, 6, 0, "preventa", "Hasta el sábado 24."),
                  ("General", 3500, 200, 6, 0, "general", None),
                  ("VIP con 2 consumos", 6000, 50, 4, 2, "vip", "Ingreso preferente y dos tragos de la carta."),
                  ("En puerta", 4000, 100, 6, 0, "puerta", "Se vende en la puerta la misma noche.")],
}


def slug_libre(titulo):
    base = sin_tildes(titulo).lower()
    base = re.sub(r"[^a-z0-9]+", "-", base).strip("-")
    slug, n = base, 2
    while slug in SLUGS:
        slug, n = f"{base}-{n}", n + 1
    SLUGS.add(slug)
    return slug


def dias_desde_pago(fecha):
    ultimo = (dt.date(fecha.year + (fecha.month == 12), fecha.month % 12 + 1, 1) - dt.timedelta(days=1)).day
    if fecha.day == ultimo:
        return 0
    return fecha.day - 15 if fecha.day >= 15 else fecha.day


FERIADOS = [(1, 1), (5, 1), (6, 29), (7, 28), (7, 29), (8, 30), (10, 8), (11, 1), (12, 8), (12, 25)]


def feriado_cerca(fecha):
    return any(m == fecha.month and abs(d - fecha.day) <= 1 for m, d in FERIADOS)


def ocupacion(fecha, cabeza, extra):
    """Qué fracción del aforo entra esa noche. Efectos multiplicativos y algo de ruido."""
    o = TIRON[cabeza] * extra
    dia = fecha.weekday()  # 0 lunes ... 6 domingo
    o *= {3: 0.62, 4: 1.0, 5: 1.33, 6: 0.74, 0: 1.05}.get(dia, 0.8)
    if dias_desde_pago(fecha) <= 3:
        o *= 1.12
    if feriado_cerca(fecha):
        o *= 1.18
    if fecha.month == 7 and fecha.day >= 24:
        o *= 1.08
    o *= math.exp(R.gauss(0, 0.10))
    return min(o, 0.96)


# ─────────────────────────── clientes ───────────────────────────

CLIENTES = {}  # id -> dict
POR_GENERO = {True: [], False: []}


def registrar_cliente(c):
    CLIENTES[c["id"]] = c
    POR_GENERO[c["mujer"]].append(c["id"])


# Los que ya había: se les da una ficha creíble en vez de los datos de relleno.
for cid, nombre, ap, correo, tel, nac, distrito, consiente, visitas, gasto, ultima, creado in consulta(
        'select id, nombre, apellido, correo, telefono, "fechaNacimiento", distrito, "consienteMarketing", '
        'visitas, "gastoTotalCentimos", "ultimaVisitaEn", "creadoEn" from "Cliente"'):
    mujer = nombre.split()[0] in MUJERES or nombre in COMPUESTOS_M
    # Una ficha de prueba hecha a mano («dsa das»): se le pone un nombre de persona.
    renombrado = nombre.strip().lower() in ("dsa", "test", "prueba", "asd") or len(nombre.strip()) < 3
    if renombrado:
        nombre, ap, _ = persona(True)
        mujer = True
        S.sql(f'update "Entrada" set "nombreTitular" = {q(nombre + " " + ap)} where "pedidoId" in '
              f'(select id from "Pedido" where "clienteId" = {q(cid)})')
    ap2 = apellido()
    falso = renombrado or correo.endswith(".test") or (tel or "").startswith("6100")
    nuevo = dict(id=cid, mujer=mujer, nombre=nombre, apellido=ap, visitas=int(visitas), gasto=int(gasto),
                 ultima=dt.datetime.fromisoformat(ultima) if ultima else None,
                 creado=dt.datetime.fromisoformat(creado), existente=True, peso=R.lognormvariate(0, 0.9),
                 primera=None)
    if falso or nac.endswith("-01-01") or re.match(r"19\d\d-(\d\d)-\1$", nac or ""):
        nueva_fecha = nacimiento() or dt.date(2001, R.randint(1, 12), R.randint(1, 28))
        S.sql(f'update "Cliente" set nombre = {q(nombre)}, apellido = {q(ap)}, '
              f'correo = {q(correo_para(nombre, ap, ap2, nueva_fecha))}, telefono = {q(telefono())}, '
              f'"fechaNacimiento" = {q(nueva_fecha)} where id = {q(cid)}')
    registrar_cliente(nuevo)

# Los pedidos que ya había: algunos se crearon "después" de pagarse porque venían de un seed.
S.sql('update "Pedido" set "creadoEn" = "pagadoEn" - interval \'3 minutes\' where "pagadoEn" < "creadoEn"')
S.sql('update "MovimientoFidelidad" m set "creadoEn" = p."pagadoEn" from "Pedido" p '
      'where p.id = m."pedidoId" and p."pagadoEn" is not null')
S.sql("""update "TipoEntrada" set nombre = 'Preventa' where nombre = 'Early Bird'""")
for cid, primera in consulta('select "clienteId", min("creadoEn") from "Pedido" group by "clienteId"'):
    if cid in CLIENTES:
        CLIENTES[cid]["primera"] = dt.datetime.fromisoformat(primera)


def cliente_nuevo(mujer, cuando):
    nombre, ap1, ap2 = persona(mujer)
    nac = nacimiento()
    consiente = R.random() < 0.54
    creado = cuando - dt.timedelta(minutes=R.randint(1, 4))
    c = dict(id=nuevo_id(), mujer=mujer, nombre=nombre, apellido=ap1, visitas=0, gasto=0, ultima=None,
             creado=creado, existente=False, peso=R.lognormvariate(0, 1.0), primera=creado)
    S.fila("Cliente", id=c["id"], nombre=nombre, apellido=ap1, correo=correo_para(nombre, ap1, ap2, nac),
           telefono=telefono() if R.random() < 0.88 else None, fechaNacimiento=nac, distrito=elegir(DISTRITOS),
           consienteMarketing=consiente, consintioEn=creado if consiente else None, visitas=0,
           gastoTotalCentimos=0, ultimaVisitaEn=None, creadoEn=creado)
    registrar_cliente(c)
    return c


def elegir_cliente(mujer, cuando, ya_vinieron, prob_repetir):
    pool = POR_GENERO[mujer]
    if pool and R.random() < prob_repetir:
        for _ in range(12):
            # Muestreo por "lealtad": los habituales vienen mucho más.
            cid = R.choices(pool, weights=[CLIENTES[p]["peso"] for p in pool], k=1)[0]
            c = CLIENTES[cid]
            if cid not in ya_vinieron and c["creado"] < cuando:
                return c
    return cliente_nuevo(mujer, cuando)


# ─────────────────────────── pedidos, entradas, accesos ───────────────────────────

FIDELIDAD = []


def nuevo_codigo():
    while True:
        c = "DSC-" + "".join(R.choice(ALFABETO) for _ in range(6))
        if c not in CODIGOS:
            CODIGOS.add(c)
            return c


def hora_llegada(es_lista, fecha):
    if es_lista:
        minutos = normal_acotada(22 * 60 + 40, 32, 21 * 60 + 10, 23 * 60 + 28)
    else:
        minutos = normal_acotada(23 * 60 + 50, 58, 21 * 60 + 15, 25 * 60 + 45)
    return lima(fecha, 0, int(minutos), R.randint(0, 59))


def crear_pedido(evento, tipo, cliente, cantidad, creado, pagado, metodo, canal, promotor, llegada, asiste,
                 pendiente=False):
    pid = nuevo_id()
    codigo = nuevo_codigo()
    subtotal = tipo["precio"] * cantidad
    comision = round(subtotal * 0.05) if canal == "WEB" else 0
    total = subtotal + comision
    S.fila("Pedido", id=pid, codigo=codigo, clienteId=cliente["id"], eventoId=evento["id"], promotorId=promotor,
           subtotalCentimos=subtotal, comisionCentimos=comision, totalCentimos=total,
           estado="PENDIENTE" if pendiente else "PAGADO", metodoPago=None if pendiente else metodo,
           refPago=None if pendiente else (f"SIMULADO-{codigo}" if canal == "WEB" else None),
           creadoEn=creado, pagadoEn=None if pendiente else pagado)
    S.fila("LineaPedido", id=nuevo_id(), pedidoId=pid, tipoEntradaId=tipo["id"], cantidad=cantidad,
           precioUnitarioCentimos=tipo["precio"])
    tipo["vendidas"] += cantidad
    if pendiente:
        return 0
    usadas = 0
    for i in range(cantidad):
        eid = nuevo_id()
        usada = asiste and llegada is not None and (R.random() < 0.985)
        S.fila("Entrada", id=eid, codigo=f"{eid}.{firma(eid)}", pedidoId=pid, tipoEntradaId=tipo["id"],
               eventoId=evento["id"], nombreTitular=f'{cliente["nombre"]} {cliente["apellido"]}', dniTitular=None,
               estado="USADA" if usada else "VALIDA", emitidaEn=pagado)
        if usada:
            cuando = llegada + dt.timedelta(seconds=R.randint(0, 420) if i else 0)
            S.fila("Acceso", id=nuevo_id(), entradaId=eid, eventoId=evento["id"],
                   escaneadoPorId=PORTERO if R.random() < 0.58 else RUBEN, puertaId=PUERTA_PRINCIPAL,
                   escaneadoEn=cuando)
            usadas += 1
    # Lo mismo que Compra.confirmarPago: ficha, puntos e interacción.
    cliente["visitas"] += 1
    cliente["gasto"] += total
    if cliente["ultima"] is None or pagado > cliente["ultima"]:
        cliente["ultima"] = pagado
    if cliente["primera"] is None or creado < cliente["primera"]:
        cliente["primera"] = creado
    S.fila("MovimientoFidelidad", id=nuevo_id(), clienteId=cliente["id"], puntos=round(total / 100),
           motivo=f"Compra {codigo}", pedidoId=pid, creadoEn=pagado)
    palabra = "entrada" if cantidad == 1 else "entradas"
    resumen = (f"Compró {cantidad} {palabra} ({codigo})." if canal == "WEB"
               else f"Compró {cantidad} {palabra} en la puerta ({codigo}).")
    S.fila("Interaccion", id=nuevo_id(), clienteId=cliente["id"], tipo="COMPRA", canal=canal, resumen=resumen,
           metadatos=None, ocurrioEn=pagado)
    return usadas


def tamano_grupo(clave):
    if clave == "lista":
        return elegir([(1, 10), (2, 30), (3, 24), (4, 20), (5, 10), (6, 6)])
    if clave == "vip":
        return elegir([(1, 10), (2, 45), (3, 15), (4, 30)])
    return elegir([(1, 30), (2, 36), (3, 12), (4, 13), (5, 5), (6, 4)])


METODO_ONLINE = [("YAPE", 42), ("TARJETA", 37), ("PLIN", 21)]
METODO_PUERTA = [("EFECTIVO", 61), ("YAPE", 32), ("TARJETA", 7)]


def vender_noche(evento, objetivo, futura_fraccion=None):
    """Genera los pedidos de una noche hasta que entre `objetivo` gente (o se llene).
    Si `futura_fraccion` viene, la noche no ha pasado: solo hay venta anticipada."""
    tipos = evento["tipos"]
    por_clave = defaultdict(list)
    for t in tipos:
        por_clave[t["clave"]].append(t)
    ya = set()
    entraron = 0
    fecha = evento["fecha"]
    puertas = lima(fecha, 21)
    prob_repetir = min(0.72, 0.30 + 0.012 * len(CLIENTES) / 25)
    pesos = {"lista": 32 if evento["plantilla"] == "barra_libre" else 20, "preventa": 30, "general": 34,
             "puerta": 26, "vip": 6}
    intentos = 0
    pedidos = 0
    while intentos < 4000:
        intentos += 1
        if futura_fraccion is not None:
            # En venta anticipada no hay puerta ni lista del mismo día.
            disponibles = [k for k in ("preventa", "general", "vip", "lista")
                           if any(t["vendidas"] < t["cantidad"] * (futura_fraccion if k != "preventa" else min(1, futura_fraccion * 2.1))
                                  for t in por_clave.get(k, []))]
        else:
            if entraron >= objetivo:
                break
            disponibles = [k for k, ts in por_clave.items() if any(t["vendidas"] < t["cantidad"] for t in ts)]
        if not disponibles:
            break
        clave = elegir([(k, pesos[k]) for k in disponibles])
        tipo = por_clave[clave][0]
        cantidad = min(tamano_grupo(clave), tipo["cantidad"] - tipo["vendidas"], tipo["max"])
        if cantidad <= 0:
            continue
        mujer = clave == "lista" or R.random() < 0.47
        if clave == "puerta":
            llegada = hora_llegada(False, fecha)
            creado = llegada - dt.timedelta(minutes=R.randint(1, 3), seconds=R.randint(0, 59))
            pagado = creado + dt.timedelta(seconds=R.randint(25, 95))
            metodo, canal, promotor = elegir(METODO_PUERTA), "EN_PERSONA", None
        else:
            if clave == "preventa":
                dias = R.choice([2, 3, 3, 4, 4, 5, 6, 7, 8, 9, 11])
            elif clave == "vip":
                dias = R.randint(1, 10)
            elif clave == "lista":
                dias = elegir([(0, 40), (1, 35), (2, 15), (3, 10)])
            else:
                dias = elegir([(0, 38), (1, 30), (2, 14), (3, 9), (5, 5), (8, 4)])
            hora = elegir([(11, 3), (13, 4), (15, 4), (17, 6), (19, 9), (20, 10), (21, 10), (22, 8), (23, 6)])
            creado = lima(fecha - dt.timedelta(days=dias), hora, R.randint(0, 59), R.randint(0, 59))
            if creado >= puertas - dt.timedelta(minutes=20):
                creado = puertas - dt.timedelta(minutes=R.randint(25, 200))
            if futura_fraccion is not None:
                # La venta anticipada se reparte desde que se publicó, con más compras cuanto más cerca.
                desde = evento["publicado"] + dt.timedelta(hours=2)
                tramo = (lima(HOY, 0) - dt.timedelta(minutes=30)) - desde
                creado = desde + tramo * (R.random() ** 0.55)
            pagado = creado + dt.timedelta(seconds=R.randint(30, 360))
            metodo = None if tipo["precio"] == 0 else elegir(METODO_ONLINE)
            canal = "WEB"
            promotor = PIERO if R.random() < 0.07 else (PORTERO if R.random() < 0.012 else None)
            llegada = hora_llegada(clave == "lista", fecha) if futura_fraccion is None else None
        cliente = elegir_cliente(mujer, creado, ya, prob_repetir)
        ya.add(cliente["id"])
        asiste = futura_fraccion is None and R.random() < {"lista": 0.80, "preventa": 0.95, "general": 0.95,
                                                            "puerta": 1.0, "vip": 0.97}[clave]
        pendiente = futura_fraccion is not None and R.random() < 0.035 and tipo["precio"] > 0
        if pendiente:
            creado = lima(HOY, 0) - dt.timedelta(minutes=R.randint(20, 300))
        entraron += crear_pedido(evento, tipo, cliente, cantidad, creado, pagado, metodo, canal, promotor,
                                 llegada, asiste, pendiente)
        pedidos += 1
    return entraron, pedidos


# ─────────────────────────── barra ───────────────────────────

def surtido(cabeza):
    cerveza, botella, sangria, energia, agua = PERFIL.get(cabeza, (1, 1, 1, 1, 1))
    return [
        ("cerveza", 46 * cerveza), ("botella", 7.5 * botella), ("sangria", 4.5 * sangria),
        ("energia", 4 * energia), ("agua", 7 * agua), ("gaseosa", 6),
    ]


CERVEZAS = [("CER-CUS-330", 30), ("CER-PIL-330", 26), ("CER-COR-355", 16), ("CER-COR-210", 18), ("CER-STE-330", 10)]
BOTELLAS = [("RON-CAR-750", 30), ("PIS-QUE-750", 13), ("VOD-ABS-750", 14), ("WHI-JWR-750", 13),
            ("RON-FLO-750", 9), ("WHI-CHI-750", 4), ("GIN-BEE-750", 6), ("TEQ-CUE-750", 6), ("LIC-JAG-700", 5)]
MIXERS = [("GAS-COL-500", 45), ("GAS-INK-500", 25), ("GAS-GIN-355", 14), ("SIN-AGU-625", 16)]


def ticket(cabeza):
    tipo = elegir(surtido(cabeza))
    if tipo == "cerveza":
        sku = elegir(CERVEZAS)
        if sku == "CER-COR-210":
            return [(sku, elegir([(2, 70), (4, 25), (6, 5)]))]  # 2 Coronitas por S/ 12
        return [(sku, elegir([(1, 62), (2, 28), (3, 6), (6, 4)]))]
    if tipo == "botella":
        lineas = [(elegir(BOTELLAS), 1)]
        mixers = defaultdict(int)
        for _ in range(elegir([(2, 30), (3, 30), (4, 30), (6, 10)])):
            mixers[elegir(MIXERS)] += 1
        if R.random() < 0.25:
            mixers["GAS-RBU-250"] += R.randint(1, 3)
        return lineas + list(mixers.items())
    if tipo == "sangria":
        return [("PRE-SAN-JAR", elegir([(1, 85), (2, 15)]))]
    if tipo == "energia":
        return [("GAS-RBU-250", elegir([(1, 70), (2, 30)]))]
    if tipo == "agua":
        return [("SIN-AGU-625", elegir([(1, 75), (2, 25)]))]
    return [(elegir([("GAS-COL-500", 60), ("GAS-INK-500", 40)]), 1)]


VENTAS_POR_NOCHE = {}  # evento_id -> [(cuando, [(sku, cant)], metodo)]


def barra_de_noche(evento, asistentes):
    n = int(asistentes * R.uniform(0.82, 1.1))
    ventas = []
    for _ in range(n):
        minutos = normal_acotada(24 * 60 + 15, 72, 21 * 60 + 25, 25 * 60 + 55)
        cuando = lima(evento["fecha"], 0, int(minutos), R.randint(0, 59))
        ventas.append((cuando, ticket(evento["cabeza"]), elegir([("EFECTIVO", 42), ("YAPE", 33), ("TARJETA", 20), ("PLIN", 5)])))
    ventas.sort()
    VENTAS_POR_NOCHE[evento["id"]] = ventas


# ─────────────────────────── construir las noches ───────────────────────────

EVENTOS = []


def crear_evento(fecha, titulo, cabeza, teloneros, plantilla, foto, descripcion, estado, extra_o_fraccion):
    eid = nuevo_id()
    publicado = lima(fecha - dt.timedelta(days=R.randint(9, 16)), R.choice([12, 16, 18, 19, 20]), R.randint(0, 59))
    if estado != "CERRADO":
        # Una noche a la venta ya se publicó: no puede tener fecha de publicación futura.
        publicado = min(publicado, lima(HOY - dt.timedelta(days=R.randint(2, 9)), R.choice([12, 18, 19]), R.randint(0, 59)))
    evento = dict(id=eid, fecha=fecha, titulo=titulo, cabeza=cabeza, plantilla=plantilla, tipos=[],
                  publicado=publicado, slug=slug_libre(titulo), estado=estado)
    S.fila("Evento", id=eid, slug=evento["slug"], titulo=titulo, descripcion=descripcion, imagenPortada=FOTOS[foto],
           salaId=SALA_ID, aperturaPuertas=lima(fecha, 21), empiezaEn=lima(fecha, 22), terminaEn=lima(fecha, 26),
           edadMinima=18, vestimenta=VESTIMENTA, estado=estado,
           publicadoEn=None if estado == "BORRADOR" else publicado, creadoEn=publicado - dt.timedelta(hours=R.randint(20, 70)))
    for nombre, precio, cantidad, maximo, consumos, clave, desc in PLANTILLAS[plantilla]:
        tid = nuevo_id()
        hasta = None
        if clave == "preventa" and plantilla not in ("universitario",):
            jueves = fecha - dt.timedelta(days=(fecha.weekday() - 3) % 7 or 7)
            hasta = lima(jueves, 23, 59) if plantilla != "halloween" else lima(D(2026, 10, 24), 23, 59)
        evento["tipos"].append(dict(id=tid, nombre=nombre, precio=precio, cantidad=cantidad, max=maximo,
                                    clave=clave, vendidas=0, consumos=consumos, desc=desc, hasta=hasta))
    # Cartel: el residente abre, el cabeza a la hora fuerte.
    S.fila("EventoArtista", eventoId=eid, artistaId=ARTISTAS[cabeza], inicioSet=lima(fecha, 24, 30),
           finSet=lima(fecha, 26), cabezaCartel=True)
    for i, t in enumerate(teloneros):
        S.fila("EventoArtista", eventoId=eid, artistaId=ARTISTAS[t], inicioSet=lima(fecha, 22 + i),
               finSet=lima(fecha, 24, 30), cabezaCartel=False)
    EVENTOS.append(evento)
    return evento


RESUMEN = []
for fecha, titulo, cabeza, teloneros, plantilla, foto, desc, extra in NOCHES:
    ev = crear_evento(fecha, titulo, cabeza, teloneros, plantilla, foto, desc, "CERRADO", extra)
    objetivo = round(AFORO * ocupacion(fecha, cabeza, extra))
    entraron, pedidos = vender_noche(ev, objetivo)
    ev["asistentes"] = entraron
    barra_de_noche(ev, entraron)
    RESUMEN.append((fecha, titulo, cabeza, objetivo, entraron, pedidos))

for fecha, titulo, cabeza, teloneros, plantilla, foto, desc, fraccion in FUTURAS:
    ev = crear_evento(fecha, titulo, cabeza, teloneros, plantilla, foto, desc, "PUBLICADO", fraccion)
    _, pedidos = vender_noche(ev, 0, futura_fraccion=fraccion)
    ev["asistentes"] = 0
    vendidas = sum(t["vendidas"] for t in ev["tipos"])
    RESUMEN.append((fecha, titulo + " (a la venta)", cabeza, 0, vendidas, pedidos))

for fecha, titulo, cabeza, teloneros, plantilla, foto, desc, _ in BORRADORES:
    ev = crear_evento(fecha, titulo, cabeza, teloneros, plantilla, foto, desc, "BORRADOR", 0)
    ev["asistentes"] = 0

# Las cinco noches que ya había: se completan con la gente que de verdad habría ido.
EXISTENTES = []
for eid, slug, titulo, empieza, estado in consulta(
        'select id, slug, titulo, "empiezaEn", estado from "Evento" where slug not like \'prueba-auto-%\' '
        'and "creadoEn" < now() - interval \'1 day\' order by "empiezaEn"'):
    if any(e["id"] == eid for e in EVENTOS):
        continue
    empieza = dt.datetime.fromisoformat(empieza)
    fecha = (empieza - dt.timedelta(hours=5)).date()
    if fecha >= HOY:
        continue
    cabeza_row = consulta(f'select a.nombre from "EventoArtista" ea join "Artista" a on a.id = ea."artistaId" '
                          f'where ea."eventoId" = {q(eid)} order by ea."cabezaCartel" desc limit 1')
    cabeza = cabeza_row[0][0] if cabeza_row else "DJ Beto"
    tipos = []
    for tid, nombre, precio, cantidad, vendidas, maximo in consulta(
            f'select id, nombre, "precioCentimos", cantidad, vendidas, "maxPorPedido" from "TipoEntrada" '
            f'where "eventoId" = {q(eid)} order by "precioCentimos"'):
        nombre = "Preventa" if nombre == "Early Bird" else nombre
        clave = {"Preventa": "preventa", "General": "general", "VIP": "vip"}.get(nombre, "general")
        # Se deja cupo libre de sobra: las pruebas de compra usan estas noches.
        tope = {"preventa": int(cantidad) - 15, "general": int(cantidad) - 150, "vip": int(cantidad) - 30}[clave]
        tipos.append(dict(id=tid, nombre=nombre, precio=int(precio), cantidad=max(int(vendidas), tope), max=int(maximo),
                          clave=clave, vendidas=int(vendidas), cantidad_real=int(cantidad)))
    ev = dict(id=eid, fecha=fecha, titulo=titulo, cabeza=cabeza if cabeza in TIRON else "DJ Beto", plantilla="especial",
              tipos=tipos, publicado=empieza - dt.timedelta(days=12), slug=slug, estado=estado, existente=True)
    ya_entraron = int(consulta(f'select count(*) from "Acceso" where "eventoId" = {q(eid)}')[0][0])
    objetivo = round(AFORO * ocupacion(fecha, ev["cabeza"], 1.0)) - ya_entraron
    entraron, pedidos = vender_noche(ev, max(objetivo, 0))
    ev["asistentes"] = entraron + ya_entraron
    barra_de_noche(ev, entraron)
    EXISTENTES.append(ev)
    EVENTOS.append(ev)
    RESUMEN.append((fecha, titulo + " (ya existía)", ev["cabeza"], objetivo + ya_entraron, ev["asistentes"], pedidos))

# Los tipos de entrada: los nuevos se insertan con lo vendido; los existentes, se actualiza el contador.
for ev in EVENTOS:
    for t in ev["tipos"]:
        if ev.get("existente"):
            S.sql(f'update "TipoEntrada" set vendidas = {t["vendidas"]} where id = {q(t["id"])}')
        else:
            S.fila("TipoEntrada", id=t["id"], eventoId=ev["id"], nombre=t["nombre"], descripcion=t["desc"],
                   precioCentimos=t["precio"], cantidad=t["cantidad"], vendidas=t["vendidas"], ventaDesde=None,
                   ventaHasta=t["hasta"], maxPorPedido=t["max"], incluyeConsumo=t["consumos"], activo=True)

# ─────────────────────────── stock, compras y cajas ───────────────────────────

stock = {sku: p["stock"] for sku, p in PRODUCTOS.items()}
consumo_semana = defaultdict(int)
for ventas in VENTAS_POR_NOCHE.values():
    for _, lineas, _ in ventas:
        for sku, c in lineas:
            consumo_semana[sku] += c
semanas = 17
PAR = {sku: max(PRODUCTOS[sku]["alerta"] * 2, int(consumo_semana[sku] / semanas * 1.35)) for sku in PRODUCTOS}

noches_ordenadas = sorted([e for e in EVENTOS if e["id"] in VENTAS_POR_NOCHE], key=lambda e: e["fecha"])
compras_por_jueves = defaultdict(lambda: defaultdict(int))
factura = {"cerveza": 48211, "gaseosas": 90314, "licores": 3127, "licores2": 1840}


def reponer(dia, faltas):
    """Una compra por proveedor ese día, con su orden y sus movimientos."""
    por_proveedor = defaultdict(list)
    for sku, unidades in faltas.items():
        if unidades > 0:
            por_proveedor[PROVEEDOR_DE[sku]].append((sku, unidades))
    for prov, lineas in por_proveedor.items():
        factura[prov] += R.randint(3, 40)
        serie = {"cerveza": "F203", "gaseosas": "F061", "licores": "F001", "licores2": "E001"}[prov]
        referencia = f"Factura {serie}-{factura[prov]:07d}"
        recibido = lima(dia, R.randint(10, 15), R.randint(0, 59))
        oc = nuevo_id()
        total = sum(PRODUCTOS[s]["coste"] * u for s, u in lineas)
        S.fila("OrdenCompra", id=oc, proveedorId=PROVEEDORES[prov], estado="RECIBIDO", totalCentimos=total,
               esperadoEn=lima(dia, 10), recibidoEn=recibido, creadoEn=recibido - dt.timedelta(days=R.randint(1, 3), hours=R.randint(0, 5)))
        for sku, u in lineas:
            S.fila("LineaOrdenCompra", id=nuevo_id(), ordenCompraId=oc, productoId=PRODUCTOS[sku]["id"], cantidad=u,
                   costeUnitarioCentimos=PRODUCTOS[sku]["coste"])
            S.fila("MovimientoStock", id=nuevo_id(), productoId=PRODUCTOS[sku]["id"], salaId=SALA_ID, tipo="COMPRA",
                   cantidad=u, costeUnitarioCentimos=PRODUCTOS[sku]["coste"], referencia=referencia,
                   usuarioId=GERENTE, creadoEn=recibido)
            stock[sku] += u


ultima_reposicion = None
CAJAS_EXISTENTES = {e: (cid, int(f), int(esp), int(cont)) for cid, e, f, esp, cont in consulta(
    'select s.id, v."eventoId", s."fondoCentimos", s."esperadoCentimos", s."contadoCentimos" from "SesionCaja" s '
    'join (select distinct "sesionCajaId", "eventoId" from "VentaBarra") v on v."sesionCajaId" = s.id')}
ultimo_mes_ajuste = None
for ev in noches_ordenadas:
    fecha = ev["fecha"]
    # Reposición el jueves (o el día antes si la noche es entre semana).
    dia_compra = fecha - dt.timedelta(days=(fecha.weekday() - 3) % 7) if fecha.weekday() >= 3 else fecha - dt.timedelta(days=1)
    if dia_compra != ultima_reposicion:
        ultima_reposicion = dia_compra
        # Cuánto hace falta para el fin de semana que viene.
        faltas = {}
        for sku in PRODUCTOS:
            if stock[sku] < PAR[sku]:
                caja = caja_de(sku)
                faltas[sku] = math.ceil((PAR[sku] - stock[sku]) / caja) * caja
        reponer(dia_compra, faltas)
    # Fin de mes: conteo físico, que siempre sale algo distinto.
    if ultimo_mes_ajuste != fecha.month and fecha.day >= 25:
        ultimo_mes_ajuste = fecha.month
        for sku in R.sample(list(PRODUCTOS), 2):
            d = R.choice([-1, -1, 1, -2])
            if stock[sku] + d >= 0:
                S.fila("MovimientoStock", id=nuevo_id(), productoId=PRODUCTOS[sku]["id"], salaId=SALA_ID, tipo="AJUSTE",
                       cantidad=d, costeUnitarioCentimos=0, referencia="Conteo físico de fin de mes",
                       usuarioId=GERENTE, creadoEn=lima(fecha, 12, 30))
                stock[sku] += d

    ventas = VENTAS_POR_NOCHE[ev["id"]]
    existente = CAJAS_EXISTENTES.get(ev["id"])
    bartender = BARTENDER if R.random() < 0.6 else LIZBETH
    if existente:
        caja_id, fondo, esperado_previo, contado_previo = existente
    else:
        caja_id, fondo = nuevo_id(), R.choice([20000, 20000, 20000, 15000, 30000])
    efectivo = 0
    for cuando, lineas, metodo in ventas:
        # Si algo se acabó en la barra, se cobra otra cosa: no hay stock negativo.
        lineas = [(s, c) for s, c in lineas if stock[s] >= c]
        if not lineas and stock["SIN-AGU-625"] >= 1:
            lineas = [("SIN-AGU-625", 1)]
        if not lineas:
            continue
        vid = nuevo_id()
        total = sum(PRODUCTOS[s]["precio"] * c for s, c in lineas)
        S.fila("VentaBarra", id=vid, sesionCajaId=caja_id, eventoId=ev["id"], totalCentimos=total, metodoPago=metodo,
               creadoEn=cuando)
        for s, c in lineas:
            S.fila("LineaVentaBarra", id=nuevo_id(), ventaBarraId=vid, productoId=PRODUCTOS[s]["id"], cantidad=c,
                   precioUnitarioCentimos=PRODUCTOS[s]["precio"])
            S.fila("MovimientoStock", id=nuevo_id(), productoId=PRODUCTOS[s]["id"], salaId=SALA_ID, tipo="VENTA",
                   cantidad=-c, costeUnitarioCentimos=PRODUCTOS[s]["coste"], referencia=f"Venta {vid}",
                   usuarioId=bartender, creadoEn=cuando)
            stock[s] -= c
        if metodo == "EFECTIVO":
            efectivo += total
    # Una botella rota de vez en cuando.
    if R.random() < 0.22:
        sku = elegir([("CER-CUS-330", 3), ("CER-PIL-330", 3), ("RON-CAR-750", 1), ("PIS-QUE-750", 1), ("PRE-SAN-JAR", 1)])
        if stock[sku] >= 1:
            S.fila("MovimientoStock", id=nuevo_id(), productoId=PRODUCTOS[sku]["id"], salaId=SALA_ID, tipo="MERMA",
                   cantidad=-1, costeUnitarioCentimos=0,
                   referencia=R.choice(["Se rompió en la barra", "Botella rota al reponer", "Devuelta por el cliente"]),
                   usuarioId=bartender, creadoEn=lima(fecha, 25, R.randint(0, 50)))
            stock[sku] -= 1
    descuadre = elegir([(0, 62), (-500, 8), (-1000, 6), (-1250, 3), (-200, 5), (300, 4), (500, 4), (-2000, 3),
                        (1000, 2), (-150, 3)])
    if existente:
        esperado = esperado_previo + efectivo
        S.sql(f'update "SesionCaja" set "esperadoCentimos" = {esperado}, "contadoCentimos" = {esperado + (contado_previo - esperado_previo)} '
              f'where id = {q(caja_id)}')
    else:
        esperado = fondo + efectivo
        S.fila("SesionCaja", id=caja_id, salaId=SALA_ID, usuarioId=bartender, fondoCentimos=fondo,
               esperadoCentimos=esperado, contadoCentimos=esperado + descuadre, descuadreCentimos=descuadre,
               abiertaEn=lima(fecha, 20, R.randint(35, 55)), cerradaEn=lima(fecha, 26, R.randint(20, 55)))

# La reposición del jueves 8 está pedida y sin llegar.
oc = nuevo_id()
lineas = [(s, caja_de(s) * R.randint(2, 4)) for s in ("CER-CUS-330", "CER-PIL-330", "CER-COR-210", "CER-COR-355")]
S.fila("OrdenCompra", id=oc, proveedorId=PROVEEDORES["cerveza"], estado="ENVIADO",
       totalCentimos=sum(PRODUCTOS[s]["coste"] * u for s, u in lineas), esperadoEn=lima(D(2026, 10, 8), 10),
       recibidoEn=None, creadoEn=lima(D(2026, 10, 4), 11, 12))
for s, u in lineas:
    S.fila("LineaOrdenCompra", id=nuevo_id(), ordenCompraId=oc, productoId=PRODUCTOS[s]["id"], cantidad=u,
           costeUnitarioCentimos=PRODUCTOS[s]["coste"])

for sku, p in PRODUCTOS.items():
    if p["nuevo"]:
        S.fila("Producto", id=p["id"], sku=sku, nombre=p["nombre"], categoriaId=p["categoria"], unidad=p["unidad"],
               costeCentimos=p["coste"], precioCentimos=p["precio"], alertaStock=p["alerta"], activo=True, stock=stock[sku])
    else:
        S.sql(f'update "Producto" set stock = {stock[sku]} where id = {q(p["id"])}')

# ─────────────────────────── gastos ───────────────────────────

for nombre in ("Publicidad", "Limpieza"):
    if nombre not in CAT_GASTO:
        CAT_GASTO[nombre] = nuevo_id()
        S.fila("CategoriaGasto", id=CAT_GASTO[nombre], nombre=nombre, activo=True)

CACHE = {"DJ Beto": 35000, "La Ruka": 90000, "DJ Chino Vega": 120000, "Kandela Crew": 180000,
         "La Combinación Perfecta": 320000, "Sonido Chicha": 150000, "Bruno Paz": 140000, "DJ Fabri": 110000,
         "Nena Sur": 160000, "Kike Valdivia": 90000}
for ev in EVENTOS:
    if ev.get("existente") or ev["fecha"] >= HOY:
        continue
    fecha = ev["fecha"]
    S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Caché artista"],
           importeCentimos=CACHE[ev["cabeza"]], descripcion=f'Caché {ev["cabeza"]}', usuarioId=GERENTE,
           fechaGasto=lima(fecha, 26, 40))
    vigilantes = 5 if ev["asistentes"] < 220 else (7 if ev["asistentes"] < 380 else 8)
    S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Seguridad"],
           importeCentimos=vigilantes * 9000, descripcion=f"{vigilantes} vigilantes", usuarioId=GERENTE,
           fechaGasto=lima(fecha, 26, 45))
    S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Limpieza"], importeCentimos=12000,
           descripcion="Limpieza al cierre (2 personas)", usuarioId=GERENTE, fechaGasto=lima(fecha + dt.timedelta(days=1), 11))
    if "stripper" in ev["titulo"].lower():
        S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Caché artista"], importeCentimos=120000,
               descripcion="Show de stripper", usuarioId=GERENTE, fechaGasto=lima(fecha, 26, 40))
    if fecha.weekday() == 5 and R.random() < 0.8:
        S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Publicidad"],
               importeCentimos=R.choice([15000, 20000, 25000, 30000, 18000]), descripcion="Anuncios en Instagram y Facebook",
               usuarioId=DIRECTOR, fechaGasto=lima(fecha - dt.timedelta(days=4), 17))
for mes in (6, 7, 8, 9):
    S.fila("Gasto", id=nuevo_id(), eventoId=None, categoriaId=CAT_GASTO["APDAYC"], importeCentimos=38000,
           descripcion=f"Licencia APDAYC {['', '', '', '', '', '', 'junio', 'julio', 'agosto', 'septiembre'][mes]}",
           usuarioId=DIRECTOR, fechaGasto=lima(D(2026, mes, 28), 12))
    S.fila("Gasto", id=nuevo_id(), eventoId=None, categoriaId=CAT_GASTO["Suministros"],
           importeCentimos=R.choice([128400, 134900, 141250, 152300]), descripcion="Recibo de luz (SEAL) y agua (SEDAPAR)",
           usuarioId=DIRECTOR, fechaGasto=lima(D(2026, mes, 18), 10))

# ─────────────────────────── reservas de box ───────────────────────────

NOTAS = ["Cumpleaños, traen torta", "Llegan a las 11:30", "Piden que esté cerca de la cabina", None, None, None,
         "Despedida de soltera", "Grupo de la promo de Medicina", None, "Cumpleaños de su enamorada"]
for ev in EVENTOS:
    if ev["fecha"].weekday() not in (4, 5) or ev["estado"] == "BORRADOR":
        continue
    pasada = ev["fecha"] < HOY
    n = R.randint(3, 7) if ev["fecha"].weekday() == 5 else R.randint(1, 4)
    if not pasada:
        n = max(0, int(n * (0.5 if (ev["fecha"] - HOY).days < 8 else 0.25)))
    libres = [b for b in BOXES if (ev["id"], b["id"]) not in RESERVAS_EXISTENTES]
    for box in R.sample(libres, min(n, len(libres))):
        creado = lima(ev["fecha"] - dt.timedelta(days=R.randint(1, 6)), R.randint(11, 22), R.randint(0, 59))
        if creado > lima(HOY, 0):
            creado = lima(HOY, 0) - dt.timedelta(hours=R.randint(2, 40))
        candidatos = [c for c in CLIENTES.values() if c["creado"] < creado]
        cliente_id = R.choices(candidatos, weights=[c["peso"] ** 2 for c in candidatos], k=1)[0]["id"]
        if pasada:
            estado = elegir([("SENTADA", 74), ("NO_VINO", 10), ("CANCELADO", 16)])
        else:
            estado = elegir([("CONFIRMADA", 60), ("SOLICITADA", 40)])
        personas = R.randint(max(3, box["plazas"] - 4), box["plazas"])
        deposito = 0 if estado == "SOLICITADA" else (20000 if box["consumo"] >= 40000 else 10000)
        S.fila("ReservaBox", id=nuevo_id(), eventoId=ev["id"], boxId=box["id"], clienteId=cliente_id, personas=personas,
               depositoCentimos=deposito, estado=estado, notas=R.choice(NOTAS), creadoEn=creado)
        canal = elegir([("WHATSAPP", 70), ("WEB", 22), ("TELEFONO", 8)])
        resumen = f'Solicitó {box["nombre"]} para {personas} personas en "{ev["titulo"]}".'
        S.fila("Interaccion", id=nuevo_id(), clienteId=cliente_id, tipo="RESERVA", canal=canal, resumen=resumen,
               metadatos=None, ocurrioEn=creado)

# ─────────────────────────── listas del promotor ───────────────────────────

for i, (iid,) in enumerate(consulta('select i.id from "Invitado" i order by i.id')):
    # La lista que había era de una noche ya pasada: la mayoría entró.
    if R.random() < 0.68:
        S.sql(f'update "Invitado" set entro = true, "entroEn" = (select "aperturaPuertas" + interval \'{R.randint(70, 200)} minutes\' '
              f'from "Evento" e join "ListaInvitados" l on l."eventoId" = e.id where l.id = "Invitado"."listaInvitadosId") '
              f'where id = {q(iid)}')
for ev in EVENTOS:
    if ev.get("existente") or ev["estado"] == "BORRADOR" or ev["fecha"].weekday() not in (4, 5):
        continue
    if R.random() > (0.75 if ev["fecha"].weekday() == 5 else 0.35):
        continue
    pasada = ev["fecha"] < HOY
    listas = [("Lista Piero", R.randint(14, 34))]
    if R.random() < 0.3:
        nombre_cumple = R.choice(MUJERES + HOMBRES)
        listas.append((f"Cumpleaños {nombre_cumple}", R.randint(6, 14)))
    for nombre, filas in listas:
        lid = nuevo_id()
        S.fila("ListaInvitados", id=lid, eventoId=ev["id"], promotorId=PIERO, nombre=nombre,
               maxInvitados=60 if nombre == "Lista Piero" else 25)
        for _ in range(filas):
            mujer = R.random() < 0.6
            n, a1, _a2 = persona(mujer)
            entro = pasada and R.random() < 0.66
            S.fila("Invitado", id=nuevo_id(), listaInvitadosId=lid, nombreCompleto=f"{n} {a1}",
                   personas=elegir([(1, 62), (2, 26), (3, 9), (4, 3)]), entro=entro,
                   entroEn=lima(ev["fecha"], 0, int(normal_acotada(22 * 60 + 50, 40, 21 * 60 + 10, 23 * 60 + 55))) if entro else None)

# ─────────────────────────── campañas y notas ───────────────────────────

CAMPANIAS = [
    ("Cumpleañeros de julio · box y sellada gratis", "WHATSAPP", "Cumpleaños del mes", None,
     "¡Feliz mes! Este mes tu box y tu sellada van por la casa. Escríbenos al 986 678 040 con tu nombre y la fecha que quieres venir.",
     "ENVIADO", lima(D(2026, 7, 1), 18, 30), 41, 33, 9),
    ("Cumpleañeros de agosto", "WHATSAPP", "Cumpleaños del mes", None,
     "¡Feliz mes! Tu box y tu sellada van por la casa en agosto. Separa tu fecha al 986 678 040.",
     "ENVIADO", lima(D(2026, 8, 1), 18, 10), 52, 44, 13),
    ("Te extrañamos · preventa a S/ 10", "CORREO", "Dormidos 60 días", "Hace rato que no vienes",
     "Hace tiempo que no te vemos por Av. Dolores. Este viernes tienes la preventa a S/ 10 con el código VUELVE, solo hasta el jueves.",
     "ENVIADO", lima(D(2026, 8, 19), 19, 0), 287, 81, 17),
    ("Halloween VIP · reserva tu box", "CORREO", "VIP", "Tu box para Halloween ya está disponible",
     "El 31 hacemos Noche de Brujas con concurso de disfraces. Como cliente frecuente, puedes separar tu box antes que nadie respondiendo a este correo.",
     "PROGRAMADO", None, 0, 0, 0),
]
for nombre, canal, segmento, asunto, cuerpo, estado, enviada, enviados, aperturas, clics in CAMPANIAS:
    if segmento not in SEGMENTOS:
        continue
    S.fila("Campania", id=nuevo_id(), nombre=nombre, canal=canal, segmentoId=SEGMENTOS[segmento], asunto=asunto,
           cuerpo=cuerpo, estado=estado, programadaEn=lima(D(2026, 10, 12), 19) if estado == "PROGRAMADO" else enviada,
           enviadaEn=enviada, enviados=enviados, aperturas=aperturas, clics=clics, ejecucionId=None,
           creadoEn=(enviada or lima(D(2026, 10, 3), 16, 20)) - dt.timedelta(days=1, hours=R.randint(1, 6)))

QUEJAS = [
    ("QUEJA", "WHATSAPP", "Reclamó que su QR no cargaba en la puerta; se validó con el código del pedido."),
    ("QUEJA", "INSTAGRAM", "Se quejó por la demora en la barra a la 1 a. m. del sábado."),
    ("QUEJA", "WHATSAPP", "Pidió devolución porque no pudo venir; se le ofreció cambiar la entrada a otra fecha."),
    ("NOTA", "TELEFONO", "Pidió que le guarden el Reservado 2 para su cumpleaños."),
    ("NOTA", "EN_PERSONA", "Cliente frecuente, viene con su grupo casi todos los sábados."),
    ("NOTA", "WHATSAPP", "Preguntó por el precio de la sellada de Chivas; se le pasó la carta."),
    ("QUEJA", "WHATSAPP", "Le cobraron dos veces por Yape en la barra; se le devolvió al día siguiente."),
    ("NOTA", "INSTAGRAM", "Quiere armar su fiesta de promo un jueves; se le pasó el contacto de reservas."),
]
frecuentes = sorted(CLIENTES.values(), key=lambda c: -c["visitas"])[:200]
for tipo, canal, resumen in QUEJAS * 2:
    c = R.choice(frecuentes)
    if not c["ultima"]:
        continue
    cuando = c["ultima"] + dt.timedelta(hours=R.randint(10, 60))
    if cuando >= lima(HOY, 0):
        continue
    S.fila("Interaccion", id=nuevo_id(), clienteId=c["id"], tipo=tipo, canal=canal, resumen=resumen, metadatos=None,
           ocurrioEn=cuando)

# ─────────────────────────── fichas de cliente ───────────────────────────

# Las nuevas se insertaron con contadores a cero; aquí se ponen los de verdad.
for c in CLIENTES.values():
    if c["visitas"] == 0 and not c["existente"]:
        continue
    creado = min(x for x in (c["creado"], c["primera"]) if x)
    S.sql(f'update "Cliente" set visitas = {c["visitas"]}, "gastoTotalCentimos" = {c["gasto"]}, '
          f'"ultimaVisitaEn" = {q(c["ultima"])}, "creadoEn" = {q(creado)}, '
          f'"consintioEn" = case when "consienteMarketing" then {q(creado)}::timestamp else null end where id = {q(c["id"])}')

ORDEN = ["Proveedor", "CategoriaProducto", "Usuario", "Artista", "Evento", "TipoEntrada", "EventoArtista",
         "Cliente", "Pedido", "LineaPedido", "Entrada", "Acceso", "MovimientoFidelidad", "Interaccion",
         "Producto", "OrdenCompra", "LineaOrdenCompra", "SesionCaja", "VentaBarra", "LineaVentaBarra",
         "MovimientoStock", "CategoriaGasto", "Gasto", "ReservaBox", "ListaInvitados", "Invitado", "Campania"]

# Las sentencias de actualización de fichas tienen que ir DESPUÉS de insertar a los clientes nuevos.
actualizaciones_finales = [s for s in S.sentencias if s.startswith('update "Cliente" set visitas')
                           or s.startswith('update "TipoEntrada" set vendidas') or s.startswith('update "Producto" set stock')
                           or s.startswith('update "SesionCaja"')]
S.sentencias = [s for s in S.sentencias if s not in actualizaciones_finales]
ruta = os.path.join(RAIZ, "poblar.sql")
S.escribir(ruta, ORDEN)
with open(ruta, "a", encoding="utf-8") as f:
    f.write("\n-- Contadores desnormalizados, al final\n")
    for s in actualizaciones_finales:
        f.write(s + ";\n")

print(f"{'fecha':<11} {'noche':<58} {'cabeza':<24} {'obj':>4} {'entr':>4} {'ped':>4}")
for fecha, titulo, cabeza, obj, entr, ped in sorted(RESUMEN):
    print(f"{fecha} {titulo[:58]:<58} {cabeza:<24} {obj:>4} {entr:>4} {ped:>4}")
print("clientes:", len(CLIENTES), " nuevos:", sum(1 for c in CLIENTES.values() if not c["existente"]))
for tabla in ORDEN:
    if S.filas.get(tabla):
        print(f"  {tabla}: {len(S.filas[tabla])}")
bajo = [(s, stock[s], PRODUCTOS[s]["alerta"]) for s in PRODUCTOS if stock[s] < PRODUCTOS[s]["alerta"]]
print("por debajo del mínimo al acabar:", bajo)
