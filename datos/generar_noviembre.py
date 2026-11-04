"""
Segunda tanda de datos: pone la base al día a 4 de noviembre de 2026.

`generar_datos.py` dejó la base como estaba el domingo 4 de octubre: siete
noches de octubre a la venta y nada programado después de Halloween. Un mes
más tarde esas noches ya pasaron sin que nadie entrara, y la web no tiene nada
que vender. Este script, con las mismas reglas que el primero:

- **Celebra las noches del 8 al 31 de octubre**: la gente con preventa entra,
  se vende el día y en la puerta, la barra trabaja con su caja y su arqueo,
  hay turnos, gastos, reservas sentadas, listas e incidencias. Quedan CERRADAS.
- **Repone el almacén** cada jueves, recibe la orden que estaba en camino y
  añade tres productos nuevos a la carta.
- **Programa noviembre y diciembre**: noches publicadas con su cartel, sus
  entradas y la preventa que llevarían a hoy; un par de borradores; y deja
  jueves y algún fin de semana libres para que el programador de la IA tenga
  fechas que proponer.
- Campañas, gastos fijos de octubre y las fichas de cliente al día.

Se aplica una vez, encima de la base que dejó `poblar.sql`; si ya se aplicó, se
niega. La semilla es fija: volver a generar da lo mismo.

Uso:  python generar_noviembre.py      (escribe poblar-noviembre.sql al lado)
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

R = random.Random(20261104)

RAIZ = os.path.dirname(os.path.abspath(__file__))
LOCAL_YML = os.path.join(RAIZ, "..", "src", "main", "resources", "application-local.yml")
PSQL = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"
HOY = dt.date(2026, 11, 4)  # miércoles: lo último que pasó fue Halloween
D = dt.date


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
    return [linea.split("\x1f") for linea in r.stdout.decode("utf-8").splitlines() if linea]


# ─────────────────────────── utilidades (las mismas que generar_datos.py) ───────────────────────────

def nuevo_id():
    return str(uuid.uuid4())


def lima(fecha, horas, minutos=0, segundos=0):
    """Hora de la sala (UTC-5) a UTC sin zona. `horas` puede pasar de 24."""
    base = dt.datetime(fecha.year, fecha.month, fecha.day)
    return base + dt.timedelta(hours=horas + 5, minutes=minutos, seconds=segundos)


AHORA = lima(HOY, 0) - dt.timedelta(minutes=30)  # lo más tarde que puede haber pasado algo


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


def fecha_db(texto):
    return dt.datetime.fromisoformat(texto) if texto else None


def sin_tildes(s):
    s = s.replace("ñ", "n").replace("Ñ", "N")
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def elegir(pares):
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
    def __init__(self):
        self.filas = defaultdict(list)
        self.columnas = {}
        self.antes = []
        self.despues = []

    def fila(self, tabla, **valores):
        if tabla not in self.columnas:
            self.columnas[tabla] = list(valores)
        self.filas[tabla].append(valores)

    def escribir(self, ruta, orden):
        with open(ruta, "w", encoding="utf-8") as f:
            f.write("-- Generado por generar_noviembre.py. Aplicar con: psql -1 -v ON_ERROR_STOP=1 -f poblar-noviembre.sql\n")
            f.write("SET client_encoding = 'UTF8';\n\n-- Cambios sobre lo que ya había\n")
            for s in self.antes:
                f.write(s.rstrip(";") + ";\n")
            for tabla in orden:
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
            f.write("\n-- Contadores desnormalizados, al final\n")
            for s in self.despues:
                f.write(s.rstrip(";") + ";\n")


S = Salida()

# ─────────────────────────── lo que ya hay ───────────────────────────

if consulta("""select 1 from "Evento" where slug = 'noche-de-brujas-halloween' and estado = 'CERRADO'"""):
    raise SystemExit("Esta tanda ya está aplicada (Halloween figura como cerrada).")

SALA_ID, AFORO = consulta('select id, aforo from "Sala" order by aforo desc limit 1')[0]
AFORO = int(AFORO)
PUERTA_PRINCIPAL = consulta("""select id from "Puerta" where not "esSalidaEmergencia" order by nombre limit 1""")[0][0]
USUARIOS = {}
for uid, correo, nombre in consulta('select id, correo, nombre from "Usuario"'):
    USUARIOS[nombre] = uid
    if correo:
        USUARIOS[correo] = uid
GERENTE = USUARIOS["sala@ssiomama.test"]
DIRECTOR = USUARIOS["admin@ssiomama.test"]
PORTERO = USUARIOS["puerta@ssiomama.test"]
BARTENDER = USUARIOS["barra@ssiomama.test"]
PIERO = USUARIOS["Piero Lazo"]
LIZBETH = USUARIOS["Lizbeth Huanca"]
RUBEN = USUARIOS["Rubén Apaza"]
PUESTOS = {nombre: pid for pid, nombre in consulta('select id, nombre from "Puesto"')}
CATEGORIAS = {nombre: cid for cid, nombre in consulta('select id, nombre from "CategoriaProducto"')}
CAT_GASTO = {nombre: cid for cid, nombre in consulta('select id, nombre from "CategoriaGasto"')}
SEGMENTOS = {nombre: sid for sid, nombre in consulta('select id, nombre from "Segmento"')}
BOXES = [dict(id=b, nombre=n, plazas=int(p), consumo=int(c)) for b, n, p, c in consulta(
    'select id, nombre, plazas, "consumoMinimoCentimos" from "Box" where activo order by nombre')]
RESERVAS_EXISTENTES = {(e, b) for e, b in consulta('select "eventoId", "boxId" from "ReservaBox"')}
CODIGOS = {c for (c,) in consulta('select codigo from "Pedido"')}
CORREOS = {c.lower() for (c,) in consulta('select correo from "Cliente"')}
ARTISTAS = {n: aid for aid, n in consulta('select id, nombre from "Artista"')}
SLUGS = {s for (s,) in consulta('select slug from "Evento"')}
PROVEEDORES = {}
for pid, nombre in consulta('select id, nombre from "Proveedor"'):
    if nombre.startswith("Backus"):
        PROVEEDORES["cerveza"] = pid
    elif "Lindley" in nombre:
        PROVEEDORES["gaseosas"] = pid
    elif nombre.startswith("Licores del Sur"):
        PROVEEDORES["licores2"] = pid
    elif "Santa Catalina" in nombre:
        PROVEEDORES["licores"] = pid
assert len(PROVEEDORES) == 4, PROVEEDORES

PRODUCTOS = {}
for pid, sku, nombre, coste, precio, stock, alerta in consulta(
        'select id, sku, nombre, "costeCentimos", "precioCentimos", stock, "alertaStock" from "Producto" where activo'):
    PRODUCTOS[sku] = dict(id=pid, sku=sku, nombre=nombre, coste=int(coste), precio=int(precio), stock=int(stock),
                          alerta=int(alerta), nuevo=False)

# Tres productos nuevos en la carta desde octubre.
NUEVOS = [
    ("PIS-4GA-700", "Pisco Cuatro Gallos Quebranta 700ml", "Destilados", "BOTELLA", 4800, 15000, 6),
    ("PRE-CHI-JAR", "Jarra de chilcano", "Preparados", "UNIDAD", 1100, 3500, 10),
    ("CER-CRI-330", "Cerveza Cristal 330ml", "Cerveza", "BOTELLA", 280, 1100, 48),
]
for sku, nombre, cat, unidad, coste, precio, alerta in NUEVOS:
    PRODUCTOS[sku] = dict(id=nuevo_id(), sku=sku, nombre=nombre, coste=coste, precio=precio, stock=0, alerta=alerta,
                          nuevo=True, unidad=unidad, categoria=CATEGORIAS[cat])


def proveedor_de(sku):
    if sku.startswith("CER-"):
        return "cerveza"
    if sku.startswith(("GAS-", "SIN-")):
        return "gaseosas"
    if sku in ("WHI-CHI-750", "GIN-BEE-750", "TEQ-CUE-750", "LIC-JAG-700", "RON-FLO-750"):
        return "licores2"
    return "licores"


CAJA = {"CER": 24, "GAS-RBU-250": 24, "GAS": 12, "SIN": 15, "PRE": 10}


def caja_de(sku):
    return CAJA.get(sku, CAJA.get(sku[:3], 6))


# ─────────────────────────── artistas, tirón y perfiles ───────────────────────────

TIRON = {"DJ Beto": 0.56, "La Ruka": 0.58, "DJ Chino Vega": 0.57, "Kandela Crew": 0.40,
         "La Combinación Perfecta": 0.50, "Sonido Chicha": 0.47, "Bruno Paz": 0.41, "DJ Fabri": 0.45,
         "Nena Sur": 0.46, "Kike Valdivia": 0.44}
PERFIL = {
    "DJ Beto": (1.0, 1.0, 1.0, 1.0, 1.0), "La Ruka": (1.0, 1.1, 1.1, 1.1, 1.0),
    "DJ Chino Vega": (1.05, 1.15, 0.9, 1.1, 1.0), "Kandela Crew": (0.75, 1.25, 1.2, 0.5, 0.9),
    "La Combinación Perfecta": (1.1, 1.2, 1.0, 0.5, 0.8), "Sonido Chicha": (1.6, 0.8, 0.6, 0.4, 0.8),
    "Bruno Paz": (0.7, 0.9, 0.7, 2.0, 1.8), "DJ Fabri": (0.8, 1.0, 0.8, 1.8, 1.5),
    "Nena Sur": (0.85, 0.8, 1.6, 0.8, 1.1), "Kike Valdivia": (1.2, 0.9, 1.0, 0.8, 1.0),
}
CACHE = {"DJ Beto": 35000, "La Ruka": 90000, "DJ Chino Vega": 120000, "Kandela Crew": 180000,
         "La Combinación Perfecta": 320000, "Sonido Chicha": 150000, "Bruno Paz": 140000, "DJ Fabri": 110000,
         "Nena Sur": 160000, "Kike Valdivia": 90000}

FERIADOS = [(1, 1), (5, 1), (6, 29), (7, 28), (7, 29), (8, 30), (10, 8), (11, 1), (12, 8), (12, 25)]


def dias_desde_pago(fecha):
    ultimo = (dt.date(fecha.year + (fecha.month == 12), fecha.month % 12 + 1, 1) - dt.timedelta(days=1)).day
    if fecha.day == ultimo:
        return 0
    return fecha.day - 15 if fecha.day >= 15 else fecha.day


def feriado_cerca(fecha):
    return any(m == fecha.month and abs(d - fecha.day) <= 1 for m, d in FERIADOS)


def ocupacion(fecha, cabeza, extra):
    o = TIRON[cabeza] * extra
    o *= {3: 0.62, 4: 1.0, 5: 1.33, 6: 0.74, 0: 1.05}.get(fecha.weekday(), 0.8)
    if dias_desde_pago(fecha) <= 3:
        o *= 1.12
    if feriado_cerca(fecha):
        o *= 1.18
    o *= math.exp(R.gauss(0, 0.10))
    return min(o, 0.96)


# ─────────────────────────── clientes ───────────────────────────

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
APELLIDOS = [
    ("Quispe", 9), ("Mamani", 9), ("Condori", 7), ("Huamaní", 4), ("Flores", 6), ("Rodríguez", 4), ("Chávez", 4),
    ("Zegarra", 4), ("Valdivia", 5), ("Delgado", 4), ("Pinto", 3), ("Salas", 4), ("Núñez", 3), ("Paredes", 4),
    ("Medina", 4), ("Begazo", 2), ("Bustamante", 2), ("Cornejo", 3), ("Llerena", 2), ("Manrique", 3),
    ("Rivera", 3), ("Villanueva", 2), ("Torres", 4), ("Apaza", 5), ("Ccama", 3), ("Choque", 4), ("Huanca", 4),
    ("Cutipa", 2), ("Ticona", 3), ("Cáceres", 3), ("Gutiérrez", 3), ("Zúñiga", 2), ("Tejada", 2), ("Carpio", 2),
    ("Linares", 2), ("Málaga", 2), ("Pacheco", 3), ("Talavera", 2), ("Vargas", 4), ("Rojas", 4), ("Gonzales", 4),
    ("Pérez", 3), ("Ramos", 3), ("Mendoza", 3), ("Herrera", 2), ("Benavente", 2), ("Arce", 2), ("Ponce", 2),
    ("Jiménez", 2), ("Fernández", 3), ("Coaquira", 2), ("Ccahua", 2), ("Yucra", 2), ("Puma", 2), ("Lazo", 2),
    ("Peralta", 2), ("Rivas", 2), ("Muñoz", 2), ("Alvarez", 2), ("Cruz", 2), ("Romero", 2), ("Soto", 2),
    ("Díaz", 3), ("Salazar", 2),
]
DISTRITOS = [("José Luis Bustamante y Rivero", 18), ("Cercado", 12), ("Cayma", 11), ("Yanahuara", 8),
             ("Cerro Colorado", 10), ("Paucarpata", 9), ("Sachaca", 5), ("Miraflores", 6), ("Mariano Melgar", 5),
             ("Socabaya", 6), ("Jacobo Hunter", 4), ("Alto Selva Alegre", 5), (None, 14)]
DOMINIOS = [("gmail.com", 70), ("hotmail.com", 14), ("outlook.com", 5), ("unsa.edu.pe", 4),
            ("ucsm.edu.pe", 3), ("ucsp.edu.pe", 2), ("yahoo.com", 2)]


def apellido():
    return elegir(APELLIDOS)


def correo_para(nombre, ap1, ap2, nac):
    n = sin_tildes(nombre.split()[0]).lower()
    a1 = sin_tildes(ap1).lower().replace(" ", "")
    a2 = sin_tildes(ap2).lower().replace(" ", "")
    anio = str(nac.year)[2:] if nac else R.choice(["99", "00", "01", "02", "03", "04", "05", "06"])
    dominio = elegir(DOMINIOS)
    if dominio.endswith(".edu.pe"):
        candidatos = [f"{n}.{a1}", f"{n[0]}{a1}{a2[0]}", f"{n}.{a1}.{a2}"]
    else:
        candidatos = [f"{n}.{a1}{anio}", f"{n}{a1}{R.randint(1, 99)}", f"{n}_{a1}", f"{n[0]}{a1}{a2[:3]}",
                      f"{n}{a1[:4]}{anio}", f"{a1}.{n}", f"{n}.{a1}.{a2[0]}"]
    base = R.choice(candidatos).strip("._")
    correo = f"{base}@{dominio}"
    while correo.lower() in CORREOS:
        correo = f"{base}{R.randint(2, 99)}@{dominio}"
    CORREOS.add(correo.lower())
    return correo


def telefono():
    return R.choice(["95", "97", "98", "99", "94", "93", "92"]) + "".join(str(R.randint(0, 9)) for _ in range(7))


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
    return nombre, apellido(), apellido()


CLIENTES = {}
POR_GENERO = {True: [], False: []}
for cid, nombre, ap, visitas, gasto, ultima, creado in consulta(
        'select id, nombre, apellido, visitas, "gastoTotalCentimos", "ultimaVisitaEn", "creadoEn" from "Cliente"'):
    mujer = nombre.split()[0] in MUJERES or nombre in COMPUESTOS_M
    c = dict(id=cid, mujer=mujer, nombre=nombre, apellido=ap or "", visitas=int(visitas), gasto=int(gasto),
             ultima=fecha_db(ultima), creado=fecha_db(creado), existente=True, tocado=False,
             # Los habituales vuelven más: la lealtad sale de las visitas que ya lleva.
             peso=(1 + int(visitas)) ** 0.8 * R.lognormvariate(0, 0.6))
    CLIENTES[cid] = c
    POR_GENERO[mujer].append(cid)


def cliente_nuevo(mujer, cuando):
    nombre, ap1, ap2 = persona(mujer)
    nac = nacimiento()
    consiente = R.random() < 0.54
    creado = cuando - dt.timedelta(minutes=R.randint(1, 4))
    c = dict(id=nuevo_id(), mujer=mujer, nombre=nombre, apellido=ap1, visitas=0, gasto=0, ultima=None,
             creado=creado, existente=False, tocado=True, peso=R.lognormvariate(0, 1.0))
    S.fila("Cliente", id=c["id"], nombre=nombre, apellido=ap1, correo=correo_para(nombre, ap1, ap2, nac),
           telefono=telefono() if R.random() < 0.88 else None, fechaNacimiento=nac, distrito=elegir(DISTRITOS),
           consienteMarketing=consiente, consintioEn=creado if consiente else None, visitas=0,
           gastoTotalCentimos=0, ultimaVisitaEn=None, creadoEn=creado)
    CLIENTES[c["id"]] = c
    POR_GENERO[mujer].append(c["id"])
    return c


def elegir_cliente(mujer, cuando, ya_vinieron, prob_repetir=0.62):
    pool = POR_GENERO[mujer]
    if pool and R.random() < prob_repetir:
        for _ in range(12):
            cid = R.choices(pool, weights=[CLIENTES[p]["peso"] for p in pool], k=1)[0]
            c = CLIENTES[cid]
            if cid not in ya_vinieron and c["creado"] < cuando:
                return c
    return cliente_nuevo(mujer, cuando)


# ─────────────────────────── pedidos, entradas, accesos ───────────────────────────

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


def acceso(entrada_id, evento_id, cuando):
    S.fila("Acceso", id=nuevo_id(), entradaId=entrada_id, eventoId=evento_id,
           escaneadoPorId=PORTERO if R.random() < 0.58 else RUBEN, puertaId=PUERTA_PRINCIPAL, escaneadoEn=cuando)


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
        usada = asiste and llegada is not None and R.random() < 0.985
        S.fila("Entrada", id=eid, codigo=f"{eid}.{firma(eid)}", pedidoId=pid, tipoEntradaId=tipo["id"],
               eventoId=evento["id"], nombreTitular=f'{cliente["nombre"]} {cliente["apellido"]}'.strip(),
               dniTitular=None, estado="USADA" if usada else "VALIDA", emitidaEn=pagado)
        if usada:
            acceso(eid, evento["id"], llegada + dt.timedelta(seconds=R.randint(0, 420) if i else 0))
            usadas += 1
    cliente["visitas"] += 1
    cliente["gasto"] += total
    cliente["tocado"] = True
    if cliente["ultima"] is None or pagado > cliente["ultima"]:
        cliente["ultima"] = pagado
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
ASISTE = {"lista": 0.80, "preventa": 0.95, "general": 0.95, "puerta": 1.0, "vip": 0.97}


def vender(evento, objetivo, futura_fraccion=None, desde=None):
    """Pedidos de una noche hasta que entren `objetivo` personas más.
    Con `futura_fraccion`, la noche no ha pasado: solo venta anticipada hasta AHORA.
    `desde`: ninguna compra nueva puede ser anterior a este instante."""
    por_clave = defaultdict(list)
    for t in evento["tipos"]:
        por_clave[t["clave"]].append(t)
    ya = set()
    entraron = 0
    fecha = evento["fecha"]
    puertas = lima(fecha, evento.get("hora_puertas", 21))
    pesos = {"lista": 32 if evento["plantilla"] == "barra_libre" else 20, "preventa": 30, "general": 34,
             "puerta": 26, "vip": 6}
    for _ in range(4000):
        if futura_fraccion is not None:
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
            if futura_fraccion is not None:
                inicio = evento["publicado"] + dt.timedelta(hours=2)
                creado = inicio + (AHORA - inicio) * (R.random() ** 0.55)
            else:
                dias = elegir([(0, 45), (1, 30), (2, 14), (3, 11)])
                hora = elegir([(11, 3), (13, 4), (15, 4), (17, 6), (19, 9), (20, 10), (21, 10), (22, 8)])
                creado = lima(fecha - dt.timedelta(days=dias), hora, R.randint(0, 59), R.randint(0, 59))
                if creado >= puertas - dt.timedelta(minutes=20):
                    creado = puertas - dt.timedelta(minutes=R.randint(25, 200))
                if desde and creado < desde:
                    creado = desde + (puertas - desde) * R.random() * 0.9
            pagado = creado + dt.timedelta(seconds=R.randint(30, 360))
            metodo = None if tipo["precio"] == 0 else elegir(METODO_ONLINE)
            canal = "WEB"
            promotor = PIERO if R.random() < 0.07 else None
            llegada = hora_llegada(clave == "lista", fecha) if futura_fraccion is None else None
        cliente = elegir_cliente(mujer, creado, ya)
        ya.add(cliente["id"])
        asiste = futura_fraccion is None and R.random() < ASISTE[clave]
        pendiente = futura_fraccion is not None and R.random() < 0.03 and tipo["precio"] > 0
        if pendiente:
            creado = AHORA - dt.timedelta(minutes=R.randint(20, 300))
        entraron += crear_pedido(evento, tipo, cliente, cantidad, creado, pagado, metodo, canal, promotor,
                                 llegada, asiste, pendiente)
    return entraron


# ─────────────────────────── barra ───────────────────────────

CERVEZAS = [("CER-CUS-330", 28), ("CER-PIL-330", 24), ("CER-COR-355", 15), ("CER-COR-210", 17),
            ("CER-STE-330", 9), ("CER-CRI-330", 10)]
BOTELLAS = [("RON-CAR-750", 28), ("PIS-QUE-750", 10), ("PIS-4GA-700", 5), ("VOD-ABS-750", 14), ("WHI-JWR-750", 13),
            ("RON-FLO-750", 9), ("WHI-CHI-750", 4), ("GIN-BEE-750", 6), ("TEQ-CUE-750", 6), ("LIC-JAG-700", 5)]
MIXERS = [("GAS-COL-500", 45), ("GAS-INK-500", 25), ("GAS-GIN-355", 14), ("SIN-AGU-625", 16)]


def ticket(cabeza):
    cerveza, botella, sangria, energia, agua = PERFIL.get(cabeza, (1, 1, 1, 1, 1))
    tipo = elegir([("cerveza", 46 * cerveza), ("botella", 7.5 * botella), ("sangria", 4.5 * sangria),
                   ("energia", 4 * energia), ("agua", 7 * agua), ("gaseosa", 6)])
    if tipo == "cerveza":
        sku = elegir(CERVEZAS)
        if sku == "CER-COR-210":
            return [(sku, elegir([(2, 70), (4, 25), (6, 5)]))]
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
        return [(elegir([("PRE-SAN-JAR", 72), ("PRE-CHI-JAR", 28)]), elegir([(1, 85), (2, 15)]))]
    if tipo == "energia":
        return [("GAS-RBU-250", elegir([(1, 70), (2, 30)]))]
    if tipo == "agua":
        return [("SIN-AGU-625", elegir([(1, 75), (2, 25)]))]
    return [(elegir([("GAS-COL-500", 60), ("GAS-INK-500", 40)]), 1)]


stock = {sku: p["stock"] for sku, p in PRODUCTOS.items()}
factura = {"cerveza": 48211 + 900, "gaseosas": 90314 + 900, "licores": 3127 + 200, "licores2": 1840 + 120}


def movimiento(sku, tipo, cantidad, coste, referencia, usuario, cuando):
    S.fila("MovimientoStock", id=nuevo_id(), productoId=PRODUCTOS[sku]["id"], salaId=SALA_ID, tipo=tipo,
           cantidad=cantidad, costeUnitarioCentimos=coste, referencia=referencia, usuarioId=usuario, creadoEn=cuando)
    stock[sku] += cantidad


def reponer(dia, faltas, estado="RECIBIDO"):
    por_proveedor = defaultdict(list)
    for sku, unidades in faltas.items():
        if unidades > 0:
            por_proveedor[proveedor_de(sku)].append((sku, unidades))
    for prov, lineas in por_proveedor.items():
        factura[prov] += R.randint(3, 40)
        serie = {"cerveza": "F203", "gaseosas": "F061", "licores": "F001", "licores2": "E001"}[prov]
        referencia = f"Factura {serie}-{factura[prov]:07d}"
        recibido = lima(dia, R.randint(10, 15), R.randint(0, 59))
        oc = nuevo_id()
        S.fila("OrdenCompra", id=oc, proveedorId=PROVEEDORES[prov], estado=estado,
               totalCentimos=sum(PRODUCTOS[s]["coste"] * u for s, u in lineas), esperadoEn=lima(dia, 10),
               recibidoEn=recibido if estado == "RECIBIDO" else None,
               creadoEn=min(recibido, AHORA) - dt.timedelta(days=R.randint(1, 3), hours=R.randint(0, 5)))
        for sku, u in lineas:
            S.fila("LineaOrdenCompra", id=nuevo_id(), ordenCompraId=oc, productoId=PRODUCTOS[sku]["id"], cantidad=u,
                   costeUnitarioCentimos=PRODUCTOS[sku]["coste"])
            if estado == "RECIBIDO":
                movimiento(sku, "COMPRA", u, PRODUCTOS[sku]["coste"], referencia, GERENTE, recibido)


# El nivel al que se repone: lo que sale en una semana normal, con margen.
consumo = {sku: int(u) for sku, u in consulta(
    'select p.sku, coalesce(sum(l.cantidad), 0) from "Producto" p left join "LineaVentaBarra" l on l."productoId" = p.id '
    'group by p.sku')}
semanas_historico = 17
PAR = {}
for sku, p in PRODUCTOS.items():
    semanal = consumo.get(sku, 0) / semanas_historico
    if p["nuevo"]:
        semanal = {"PIS-4GA-700": 6, "PRE-CHI-JAR": 30, "CER-CRI-330": 110}[sku]
    PAR[sku] = max(p["alerta"] * 2, int(semanal * 1.4))


def faltas_hasta_par(factor=1.0):
    faltas = {}
    for sku in PRODUCTOS:
        objetivo = int(PAR[sku] * factor)
        if stock[sku] < objetivo:
            caja = caja_de(sku)
            faltas[sku] = math.ceil((objetivo - stock[sku]) / caja) * caja
    return faltas


# ─────────────────────────── 1. las noches de octubre ───────────────────────────

PLANTILLA_DE_CLAVE = {"lista": "lista", "preventa": "preventa", "con carné": "preventa", "general": "general",
                      "vip": "vip", "en puerta": "puerta"}


def clave_de(nombre):
    n = nombre.lower()
    for prefijo, clave in PLANTILLA_DE_CLAVE.items():
        if n.startswith(prefijo):
            return clave
    return "general"


# Lo que la noche prometía (el mismo «extra de tirón» que en generar_datos.py).
EXTRA = {"jueves-universitario-2": 1.0, "calle-brava-7": 1.05, "sabado-de-bellaqueo-barra-libre-para-mujeres-2": 1.12,
         "viernes-de-perreo-7": 1.0, "ven-con-5-amigas-jarra-de-ron-gratis-2": 1.12,
         "la-combinacion-perfecta-en-vivo": 1.12, "noche-de-brujas-halloween": 1.25}

NOTAS_CIERRE = {
    "noche-de-brujas-halloween": "Lleno desde la 1 a. m.; se cerró la venta en puerta a la 1:40. Ganó el concurso "
                                 "un grupo disfrazado de Squid Game. Faltó hielo a las 2.",
    "la-combinacion-perfecta-en-vivo": "La orquesta empezó 20 minutos tarde por la prueba de sonido. Buena barra.",
    "sabado-de-bellaqueo-barra-libre-para-mujeres-2": "Barra libre sin incidencias; cola en la barra de 11 a 12.",
    "jueves-universitario-2": "Feriado de Angamos: jueves fuerte, mucho carné de la UNSA.",
}

INCIDENCIAS = {
    "noche-de-brujas-halloween": [
        ("SALUD", "Una asistente se descompensó en la pista; atendida en la entrada, salió acompañada.", 25, 10),
        ("SEGURIDAD", "Pelea entre dos grupos junto a la barra; se retiró a cuatro personas.", 26, 5),
        ("OPERACION", "Se acabó el hielo a las 2:00; se compró en la grifería de enfrente.", 26, 0),
    ],
    "sabado-de-bellaqueo-barra-libre-para-mujeres-2": [
        ("CLIENTE", "Reclamo por la cola en la barra durante la barra libre.", 23, 40),
    ],
    "calle-brava-7": [("OPERACION", "Se cayó el POS de la barra 15 minutos; se cobró solo efectivo y Yape.", 24, 50)],
    "la-combinacion-perfecta-en-vivo": [("PERSONAL", "Un vigilante llegó 40 minutos tarde.", 21, 40)],
}

PASADAS = []
for eid, slug, titulo, empieza in consulta(
        """select id, slug, titulo, "empiezaEn" from "Evento"
           where estado = 'PUBLICADO' and "empiezaEn" > '2026-10-05' and "empiezaEn" < '2026-11-04'
           order by "empiezaEn" """):
    fecha = (fecha_db(empieza) - dt.timedelta(hours=5)).date()
    cartel = consulta(f'select a.nombre from "EventoArtista" ea join "Artista" a on a.id = ea."artistaId" '
                      f'where ea."eventoId" = {q(eid)} order by ea."cabezaCartel" desc')
    cabeza = cartel[0][0] if cartel else "DJ Beto"
    tipos = []
    for tid, nombre, precio, cantidad, vendidas, maximo in consulta(
            f'select id, nombre, "precioCentimos", cantidad, vendidas, "maxPorPedido" from "TipoEntrada" '
            f'where "eventoId" = {q(eid)} and activo order by "precioCentimos"'):
        tipos.append(dict(id=tid, nombre=nombre, precio=int(precio), cantidad=int(cantidad), vendidas=int(vendidas),
                          max=int(maximo), clave=clave_de(nombre)))
    plantilla = "barra_libre" if any(t["clave"] == "lista" and "barra libre" in t["nombre"].lower() for t in tipos) else "regular"
    ev = dict(id=eid, slug=slug, titulo=titulo, fecha=fecha, cabeza=cabeza, tipos=tipos, plantilla=plantilla,
              teloneros=[c[0] for c in cartel[1:]])
    tipo_por_id = {t["id"]: t for t in tipos}

    # Los pedidos que se quedaron a medio pagar: caducaron y devuelven su cupo.
    for pid, tid, cant in consulta(
            f'select p.id, l."tipoEntradaId", l.cantidad from "Pedido" p join "LineaPedido" l on l."pedidoId" = p.id '
            f'where p."eventoId" = {q(eid)} and p.estado = \'PENDIENTE\''):
        S.antes.append(f'update "Pedido" set estado = \'CANCELADO\' where id = {q(pid)}')
        tipo_por_id[tid]["vendidas"] -= int(cant)

    # Quien compró antes viene (o no) esa noche.
    ya_entraron = 0
    usadas = []
    grupos = defaultdict(list)
    for entrada_id, pedido_id, tid in consulta(
            f'select e.id, e."pedidoId", e."tipoEntradaId" from "Entrada" e '
            f'where e."eventoId" = {q(eid)} and e.estado = \'VALIDA\''):
        grupos[pedido_id].append((entrada_id, tid))
    for pedido_id, entradas in grupos.items():
        clave = tipo_por_id[entradas[0][1]]["clave"]
        if R.random() >= ASISTE[clave]:
            continue
        llegada = hora_llegada(clave == "lista", fecha)
        for i, (entrada_id, _) in enumerate(entradas):
            if R.random() < 0.985:
                acceso(entrada_id, eid, llegada + dt.timedelta(seconds=R.randint(0, 420) if i else 0))
                usadas.append(entrada_id)
                ya_entraron += 1
    for i in range(0, len(usadas), 300):
        S.antes.append('update "Entrada" set estado = \'USADA\' where id in ('
                       + ", ".join(q(u) for u in usadas[i:i + 300]) + ")")

    objetivo = round(AFORO * ocupacion(fecha, cabeza if cabeza in TIRON else "DJ Beto", EXTRA.get(slug, 1.0)))
    nuevas = vender(ev, max(0, objetivo - ya_entraron), desde=lima(D(2026, 10, 4), 12))
    ev["asistentes"] = ya_entraron + nuevas
    ev["objetivo"] = objetivo
    PASADAS.append(ev)


def reservas_pasadas(ev):
    for rid, estado in consulta(f'select id, estado from "ReservaBox" where "eventoId" = {q(ev["id"])}'):
        if estado in ("CONFIRMADA", "SOLICITADA"):
            nuevo = elegir([("SENTADA", 80), ("NO_VINO", 12)]) if estado == "CONFIRMADA" else \
                elegir([("SENTADA", 45), ("CANCELADO", 55)])
            S.antes.append(f'update "ReservaBox" set estado = {q(nuevo)} where id = {q(rid)}')


# Lo que llega corto a noviembre: es lo que el agente de inventario tiene que detectar.
CORTOS = {"GAS-GIN-355", "LIC-JAG-700", "GAS-RBU-250"}

# Barra, caja, stock, turnos, gastos e incidencias de cada noche, en orden.
CAJAS_HECHAS = []
ultima_reposicion = None
# La reposición que estaba en camino (pedida el 4, para el jueves 8) llega a su hora.
for oc, prov in consulta("""select id, "proveedorId" from "OrdenCompra" where estado = 'ENVIADO' and "esperadoEn" >= '2026-10-01'"""):
    recibido = lima(D(2026, 10, 8), 11, 20)
    S.antes.append(f'update "OrdenCompra" set estado = \'RECIBIDO\', "recibidoEn" = {q(recibido)} where id = {q(oc)}')
    for pid, cant, coste in consulta(f'select "productoId", cantidad, "costeUnitarioCentimos" from "LineaOrdenCompra" '
                                     f'where "ordenCompraId" = {q(oc)}'):
        sku = next(s for s, p in PRODUCTOS.items() if p["id"] == pid)
        movimiento(sku, "COMPRA", int(cant), int(coste), "Factura F203-0049187", GERENTE, recibido)

for ev in PASADAS:
    fecha = ev["fecha"]
    dia_compra = fecha - dt.timedelta(days=(fecha.weekday() - 3) % 7) if fecha.weekday() >= 3 else fecha - dt.timedelta(days=1)
    if dia_compra != ultima_reposicion:
        ultima_reposicion = dia_compra
        # Halloween se prepara con más género: es la noche más fuerte del año. Licores del Sur
        # y Lindley no tenían Jägermeister, Red Bull ni Ginger Ale esa semana: llegan cortos.
        faltas = faltas_hasta_par(1.5 if fecha == D(2026, 10, 31) else 1.0)
        if fecha == D(2026, 10, 31):
            faltas = {s: u for s, u in faltas.items() if s not in CORTOS}
        reponer(dia_compra, faltas)
    # Fin de mes: conteo físico, que siempre sale algo distinto.
    if fecha.month == 10 and fecha.day >= 25 and "ajuste-octubre" not in CAJAS_HECHAS:
        CAJAS_HECHAS.append("ajuste-octubre")
        for sku in R.sample(sorted(PRODUCTOS), 2):
            d = R.choice([-1, -1, 1, -2])
            if stock[sku] + d >= 0:
                movimiento(sku, "AJUSTE", d, 0, "Conteo físico de fin de mes", GERENTE, lima(fecha, 12, 30))

    # Las ventas de la barra.
    n = int(ev["asistentes"] * R.uniform(0.85, 1.1))
    ventas = []
    for _ in range(n):
        minutos = normal_acotada(24 * 60 + 15, 72, 21 * 60 + 25, 25 * 60 + 55)
        ventas.append((lima(fecha, 0, int(minutos), R.randint(0, 59)), ticket(ev["cabeza"]),
                       elegir([("EFECTIVO", 40), ("YAPE", 35), ("TARJETA", 20), ("PLIN", 5)])))
    ventas.sort()
    bartender = BARTENDER if R.random() < 0.6 else LIZBETH
    caja_id, fondo = nuevo_id(), R.choice([20000, 20000, 15000, 30000])
    efectivo = 0
    for cuando, lineas, metodo in ventas:
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
            movimiento(s, "VENTA", -c, PRODUCTOS[s]["coste"], f"Venta {vid}", bartender, cuando)
        if metodo == "EFECTIVO":
            efectivo += total
    if R.random() < 0.3:
        sku = elegir([("CER-CUS-330", 3), ("CER-PIL-330", 3), ("RON-CAR-750", 1), ("PRE-SAN-JAR", 1)])
        if stock[sku] >= 1:
            movimiento(sku, "MERMA", -1, 0, R.choice(["Se rompió en la barra", "Botella rota al reponer"]),
                       bartender, lima(fecha, 25, R.randint(0, 50)))
    descuadre = elegir([(0, 60), (-500, 9), (-1000, 6), (-200, 6), (300, 5), (500, 5), (-2000, 3), (1000, 3)])
    S.fila("SesionCaja", id=caja_id, salaId=SALA_ID, usuarioId=bartender, fondoCentimos=fondo,
           esperadoCentimos=fondo + efectivo, contadoCentimos=fondo + efectivo + descuadre, descuadreCentimos=descuadre,
           abiertaEn=lima(fecha, 20, R.randint(35, 55)), cerradaEn=lima(fecha, 26, R.randint(20, 55)),
           puestoId=PUESTOS["Barra principal"])

    # El personal de la noche.
    for usuario, puesto, desde, hasta in [
            (GERENTE, "Jefa de sala", 20, 27), (PORTERO, "Control de acceso", 20.5, 26.5),
            (RUBEN, "Control de acceso", 20.5, 26.5), (bartender, "Barra principal", 20.5, 27),
            (LIZBETH if bartender == BARTENDER else BARTENDER, "Barra principal", 21.5, 26.5)]:
        if ev["asistentes"] < 200 and usuario == RUBEN:
            continue
        S.fila("Turno", id=nuevo_id(), usuarioId=usuario, eventoId=ev["id"], puestoId=PUESTOS[puesto],
               empiezaEn=lima(fecha, int(desde), int(desde % 1 * 60)), terminaEn=lima(fecha, int(hasta), int(hasta % 1 * 60)),
               publicado=True)

    # Los gastos de la noche.
    for cabeza in [ev["cabeza"]] + ev["teloneros"]:
        if cabeza in CACHE:
            S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Caché artista"],
                   importeCentimos=CACHE[cabeza] if cabeza == ev["cabeza"] else CACHE[cabeza] // 2,
                   descripcion=f"Caché {cabeza}", usuarioId=GERENTE, fechaGasto=lima(fecha, 26, 40))
    vigilantes = 5 if ev["asistentes"] < 220 else (7 if ev["asistentes"] < 380 else 8)
    S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Seguridad"],
           importeCentimos=vigilantes * 9000, descripcion=f"{vigilantes} vigilantes", usuarioId=GERENTE,
           fechaGasto=lima(fecha, 26, 45))
    S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Limpieza"], importeCentimos=12000,
           descripcion="Limpieza al cierre (2 personas)", usuarioId=GERENTE, fechaGasto=lima(fecha + dt.timedelta(days=1), 11))
    if fecha.weekday() == 5:
        S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO["Publicidad"],
               importeCentimos=R.choice([15000, 20000, 25000, 30000]), descripcion="Anuncios en Instagram y Facebook",
               usuarioId=DIRECTOR, fechaGasto=lima(fecha - dt.timedelta(days=4), 17))
    if ev["slug"] == "noche-de-brujas-halloween":
        for importe, desc, cat in [(50000, "Premio del concurso de disfraces", "Publicidad"),
                                   (68000, "Decoración de Halloween (telarañas, calabazas, humo)", "Suministros"),
                                   (4500, "Hielo de emergencia (grifo de enfrente)", "Suministros")]:
            S.fila("Gasto", id=nuevo_id(), eventoId=ev["id"], categoriaId=CAT_GASTO[cat], importeCentimos=importe,
                   descripcion=desc, usuarioId=GERENTE, fechaGasto=lima(fecha, 26, 50))

    for tipo, desc, h, m in INCIDENCIAS.get(ev["slug"], []):
        cuando = lima(fecha, h, m)
        S.fila("IncidenciaNoche", id=nuevo_id(), eventoId=ev["id"], tipo=tipo, descripcion=desc,
               registradaPorId=GERENTE, ocurrioEn=cuando, creadaEn=cuando + dt.timedelta(minutes=R.randint(5, 40)))

    reservas_pasadas(ev)
    # Quien iba en lista, entró o no.
    for iid, in consulta(f'select i.id from "Invitado" i join "ListaInvitados" l on l.id = i."listaInvitadosId" '
                         f'where l."eventoId" = {q(ev["id"])} and not i.entro'):
        if R.random() < 0.68:
            S.antes.append(f'update "Invitado" set entro = true, "entroEn" = '
                           f'{q(lima(fecha, 0, int(normal_acotada(22 * 60 + 50, 40, 21 * 60 + 10, 23 * 60 + 55))))} '
                           f'where id = {q(iid)}')

    S.antes.append(f'update "Evento" set estado = \'CERRADO\', "notasCierre" = {q(NOTAS_CIERRE.get(ev["slug"]))} '
                   f'where id = {q(ev["id"])}')
    for t in ev["tipos"]:
        S.despues.append(f'update "TipoEntrada" set vendidas = {t["vendidas"]} where id = {q(t["id"])}')

# ─────────────────────────── 2. el almacén para noviembre ───────────────────────────

# Reposición de esta mañana (miércoles 4) para el primer fin de semana de noviembre.
# Lo que llegó corto sigue corto: el proveedor no lo repone hasta la semana que viene.
faltas = {s: u for s, u in faltas_hasta_par(1.1).items() if s not in CORTOS}
reponer(D(2026, 11, 4) - dt.timedelta(days=1), faltas)  # recibido el martes 3
# Y la cerveza para el fin de semana largo, pedida y en camino.
oc_lineas = [(s, caja_de(s) * R.randint(3, 5)) for s in ("CER-CUS-330", "CER-PIL-330", "CER-CRI-330", "CER-COR-210")]
oc = nuevo_id()
S.fila("OrdenCompra", id=oc, proveedorId=PROVEEDORES["cerveza"], estado="ENVIADO",
       totalCentimos=sum(PRODUCTOS[s]["coste"] * u for s, u in oc_lineas), esperadoEn=lima(D(2026, 11, 5), 10),
       recibidoEn=None, creadoEn=lima(D(2026, 11, 3), 16, 40))
for s, u in oc_lineas:
    S.fila("LineaOrdenCompra", id=nuevo_id(), ordenCompraId=oc, productoId=PRODUCTOS[s]["id"], cantidad=u,
           costeUnitarioCentimos=PRODUCTOS[s]["coste"])

# ─────────────────────────── 3. noviembre y diciembre ───────────────────────────

FOTOS = {"perreo": "/fotos/cabina.webp", "barra": "/fotos/barra.webp", "vivo": "/fotos/envivo.webp",
         "show": "/fotos/show.webp", "fiesta": "/fotos/portada.webp", "aniversario": "/fotos/aniversario.webp"}
VESTIMENTA = "Casual. No se entra con short, sandalias ni ropa deportiva."
PROMO_CASA = "Si cumples años este mes, pide tu box y tu sellada gratis. Reservas de box y listas al 986 678 040."

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
    "anio_nuevo": [("Preventa 1", 4000, 150, 6, 0, "preventa", "Primer tramo, hasta agotar."),
                   ("General", 6000, 220, 6, 0, "general", "Incluye cotillón y brindis a las 12."),
                   ("VIP con 3 consumos", 12000, 60, 4, 3, "vip", "Ingreso preferente, zona VIP y tres tragos de la carta."),
                   ("En puerta", 8000, 60, 6, 0, "puerta", "Se vende en la puerta la misma noche, si queda aforo.")],
}

# (fecha, título, cabeza, teloneros, plantilla, foto, descripción, estado, fracción de preventa vendida)
PROXIMAS = [
    (D(2026, 11, 6), "Viernes de Perreo", "DJ Beto", [], "regular", "perreo",
     "Reguetón, dembow y salsa choke toda la noche con DJ Beto. Preventa a S/ 15 hasta el jueves.", "PUBLICADO", 0.30),
    (D(2026, 11, 12), "Jueves Universitario", "DJ Beto", [], "universitario", "fiesta",
     "S/ 10 con carné universitario hasta las 11:30 p. m. Trae a tu promo.", "PUBLICADO", 0.12),
    (D(2026, 11, 13), "Guaracha Night", "DJ Fabri", ["DJ Beto"], "regular", "show",
     "DJ Fabri con guaracha, aleteo y house latino desde la 1 a. m. Abre DJ Beto.", "PUBLICADO", 0.12),
    (D(2026, 11, 14), "Calle Brava", "DJ Chino Vega", ["DJ Beto"], "regular", "perreo",
     "2 Coronitas por S/ 12 hasta agotar stock, sellada de Cartavio a S/ 100 y jarra de chilcano a S/ 35.", "PUBLICADO", 0.16),
    (D(2026, 11, 20), "Latin Pop Night", "Nena Sur", ["DJ Beto"], "regular", "show",
     "Nena Sur en vivo con lo mejor del pop latino a la 1 a. m. Jarra de sangría a S/ 30.", "PUBLICADO", 0.07),
    (D(2026, 11, 21), "Salsa Brava con Kandela Crew", "Kandela Crew", ["La Combinación Perfecta"], "especial", "vivo",
     "Kandela Crew y La Combinación Perfecta en vivo: salsa, timba y cumbia hasta el cierre. VIP con dos consumos.",
     "PUBLICADO", 0.08),
    (D(2026, 11, 27), "Viernes de Perreo · Quincena", "DJ Beto", ["La Ruka"], "regular", "perreo",
     "Fin de mes con La Ruka y DJ Beto. Preventa a S/ 15 hasta el jueves.", "PUBLICADO", 0.04),
    (D(2026, 11, 28), "Sábado de Bellaqueo · Barra libre para mujeres", "DJ Beto", ["La Ruka"], "barra_libre", "perreo",
     "Barra libre para mujeres de 11 p. m. a 12 a. m.: whisky, ron, vodka y pisco. " + PROMO_CASA, "PUBLICADO", 0.06),
    (D(2026, 12, 5), "Noche Crossover · Retro 2000", "Kike Valdivia", ["DJ Beto"], "regular", "fiesta",
     "Del rock en español al reguetón de los 2000. Kike Valdivia toca de 12 a 2.", "PUBLICADO", 0.02),
    (D(2026, 12, 7), "Víspera de la Inmaculada", "DJ Beto", ["Sonido Chicha"], "especial", "fiesta",
     "Abrimos el lunes porque el martes 8 es feriado. Cumbia, chicha y reguetón desde las 9:00 p. m.", "BORRADOR", 0),
    (D(2026, 12, 12), "Fiesta de fin de ciclo", "DJ Beto", ["DJ Fabri"], "universitario", "fiesta",
     "Cerramos el ciclo. S/ 10 con carné hasta medianoche.", "BORRADOR", 0),
    (D(2026, 12, 31), "Año Nuevo 2027 · Ssiomama", "La Combinación Perfecta", ["DJ Beto", "DJ Chino Vega"], "anio_nuevo", "aniversario",
     "Recibimos el 2027 con orquesta en vivo, cotillón y brindis a las 12. DJ Beto y DJ Chino Vega hasta las 6 a. m. "
     "Preventa 1 a S/ 40 hasta agotar.", "PUBLICADO", 0.09),
]


def slug_libre(titulo):
    base = re.sub(r"[^a-z0-9]+", "-", sin_tildes(titulo).lower()).strip("-")
    slug, n = base, 2
    while slug in SLUGS:
        slug, n = f"{base}-{n}", n + 1
    SLUGS.add(slug)
    return slug


def tipos_de(plantilla, fecha):
    tipos = []
    for nombre, precio, cantidad, maximo, consumos, clave, desc in PLANTILLAS[plantilla]:
        hasta = None
        if clave == "preventa" and plantilla not in ("universitario", "anio_nuevo"):
            jueves = fecha - dt.timedelta(days=(fecha.weekday() - 3) % 7 or 7)
            hasta = lima(jueves, 23, 59)
        tipos.append(dict(id=nuevo_id(), nombre=nombre, precio=precio, cantidad=cantidad, max=maximo, clave=clave,
                          vendidas=0, consumos=consumos, desc=desc, hasta=hasta))
    return tipos


FUTURAS = []
for fecha, titulo, cabeza, teloneros, plantilla, foto, desc, estado, fraccion in PROXIMAS:
    eid = nuevo_id()
    lejos = (fecha - HOY).days
    publicado = lima(HOY - dt.timedelta(days=R.randint(3, 9) if lejos < 25 else R.randint(1, 4)),
                     R.choice([12, 16, 18, 19, 20]), R.randint(0, 59))
    anio_nuevo = plantilla == "anio_nuevo"
    ev = dict(id=eid, fecha=fecha, titulo=titulo, cabeza=cabeza, plantilla=plantilla, publicado=publicado,
              tipos=tipos_de(plantilla, fecha), estado=estado, hora_puertas=22 if anio_nuevo else 21)
    S.fila("Evento", id=eid, slug=slug_libre(titulo), titulo=titulo, descripcion=desc, imagenPortada=FOTOS[foto],
           salaId=SALA_ID, aperturaPuertas=lima(fecha, 22 if anio_nuevo else 21),
           empiezaEn=lima(fecha, 23 if anio_nuevo else 22), terminaEn=lima(fecha, 30 if anio_nuevo else 26),
           edadMinima=18, vestimenta="Casual elegante" if anio_nuevo else VESTIMENTA, estado=estado,
           publicadoEn=publicado if estado == "PUBLICADO" else None,
           creadoEn=publicado - dt.timedelta(hours=R.randint(20, 70)), video=None, notasCierre=None)
    S.fila("EventoArtista", eventoId=eid, artistaId=ARTISTAS[cabeza], inicioSet=lima(fecha, 24, 30),
           finSet=lima(fecha, 26), cabezaCartel=True)
    for i, t in enumerate(teloneros):
        S.fila("EventoArtista", eventoId=eid, artistaId=ARTISTAS[t], inicioSet=lima(fecha, 22 + i),
               finSet=lima(fecha, 24, 30), cabezaCartel=False)
    if estado == "PUBLICADO" and fraccion > 0:
        vender(ev, 0, futura_fraccion=fraccion)
    FUTURAS.append(ev)

# El borrador del sábado 7 que dejó la primera tanda: se publica, ya tiene su cartel y sus entradas.
BORRADOR_7 = consulta("""select id from "Evento" where slug = 'sabado-de-bellaqueo-7' and estado = 'BORRADOR'""")
if BORRADOR_7:
    eid = BORRADOR_7[0][0]
    publicado = lima(D(2026, 10, 27), 19, 12)
    S.antes.append(f'update "Evento" set estado = \'PUBLICADO\', "publicadoEn" = {q(publicado)}, titulo = '
                   f'\'Sábado de Bellaqueo · Barra libre para mujeres\', descripcion = '
                   f'{q("Barra libre para mujeres de 11 p. m. a 12 a. m.: whisky, ron, vodka y pisco. " + PROMO_CASA)} '
                   f'where id = {q(eid)}')
    tipos = []
    for tid, nombre, precio, cantidad, vendidas, maximo in consulta(
            f'select id, nombre, "precioCentimos", cantidad, vendidas, "maxPorPedido" from "TipoEntrada" '
            f'where "eventoId" = {q(eid)} and activo'):
        tipos.append(dict(id=tid, nombre=nombre, precio=int(precio), cantidad=int(cantidad), vendidas=int(vendidas),
                          max=int(maximo), clave=clave_de(nombre)))
    ev = dict(id=eid, fecha=D(2026, 11, 7), titulo="Sábado de Bellaqueo", cabeza="DJ Beto", plantilla="barra_libre",
              publicado=publicado, tipos=tipos, estado="PUBLICADO", existente=True)
    vender(ev, 0, futura_fraccion=0.38)
    for t in tipos:
        S.despues.append(f'update "TipoEntrada" set vendidas = {t["vendidas"]} where id = {q(t["id"])}')
        # La preventa vence el jueves 5 a medianoche.
        if t["clave"] == "preventa":
            S.despues.append(f'update "TipoEntrada" set "ventaHasta" = {q(lima(D(2026, 11, 5), 23, 59))} where id = {q(t["id"])}')
    FUTURAS.append(ev)

for ev in FUTURAS:
    if ev.get("existente"):
        continue
    for t in ev["tipos"]:
        S.fila("TipoEntrada", id=t["id"], eventoId=ev["id"], nombre=t["nombre"], descripcion=t["desc"],
               precioCentimos=t["precio"], cantidad=t["cantidad"], vendidas=t["vendidas"], ventaDesde=None,
               ventaHasta=t["hasta"], maxPorPedido=t["max"], incluyeConsumo=t["consumos"], activo=True)

# Reservas de box para los próximos fines de semana.
NOTAS = ["Cumpleaños, traen torta", "Llegan a las 11:30", "Piden que esté cerca de la cabina", None, None,
         "Despedida de soltera", "Grupo de la promo de Ingeniería", None, "Aniversario de la empresa"]
candidatos_box = sorted(CLIENTES.values(), key=lambda c: -c["visitas"])[:400]
for ev in FUTURAS:
    if ev["estado"] != "PUBLICADO" or ev["fecha"].weekday() not in (4, 5, 3):
        continue
    dias = (ev["fecha"] - HOY).days
    n = R.randint(3, 6) if ev["fecha"].weekday() == 5 else R.randint(1, 3)
    n = int(n * (0.9 if dias < 4 else 0.5 if dias < 12 else 0.25)) + (4 if ev["plantilla"] == "anio_nuevo" else 0)
    libres = [b for b in BOXES if (ev["id"], b["id"]) not in RESERVAS_EXISTENTES]
    for box in R.sample(libres, min(n, len(libres))):
        creado = AHORA - dt.timedelta(hours=R.randint(2, 24 * min(dias + 1, 8)))
        cliente = R.choices(candidatos_box, weights=[c["peso"] ** 2 for c in candidatos_box], k=1)[0]
        estado = elegir([("CONFIRMADA", 60), ("SOLICITADA", 40)])
        personas = R.randint(max(3, box["plazas"] - 4), box["plazas"])
        S.fila("ReservaBox", id=nuevo_id(), eventoId=ev["id"], boxId=box["id"], clienteId=cliente["id"],
               personas=personas, depositoCentimos=0 if estado == "SOLICITADA" else (20000 if box["consumo"] >= 40000 else 10000),
               estado=estado, notas=R.choice(NOTAS), creadoEn=creado)
        S.fila("Interaccion", id=nuevo_id(), clienteId=cliente["id"], tipo="RESERVA",
               canal=elegir([("WHATSAPP", 70), ("WEB", 22), ("TELEFONO", 8)]),
               resumen=f'Solicitó {box["nombre"]} para {personas} personas en "{ev["titulo"]}".', metadatos=None,
               ocurrioEn=creado)

# Listas de Piero para los dos próximos fines de semana y turnos ya publicados.
for ev in FUTURAS:
    if ev["estado"] != "PUBLICADO" or (ev["fecha"] - HOY).days > 11 or ev["fecha"].weekday() not in (4, 5):
        continue
    lid = nuevo_id()
    S.fila("ListaInvitados", id=lid, eventoId=ev["id"], promotorId=PIERO, nombre="Lista Piero", maxInvitados=60,
           liquidacionId=None)
    for _ in range(R.randint(8, 22)):
        n, a1, _ = persona(R.random() < 0.6)
        S.fila("Invitado", id=nuevo_id(), listaInvitadosId=lid, nombreCompleto=f"{n} {a1}",
               personas=elegir([(1, 62), (2, 26), (3, 9), (4, 3)]), entro=False, entroEn=None)
    for usuario, puesto, desde, hasta in [(GERENTE, "Jefa de sala", 20, 27), (PORTERO, "Control de acceso", 20.5, 26.5),
                                          (RUBEN, "Control de acceso", 20.5, 26.5), (BARTENDER, "Barra principal", 20.5, 27),
                                          (LIZBETH, "Barra principal", 21.5, 26.5)]:
        S.fila("Turno", id=nuevo_id(), usuarioId=usuario, eventoId=ev["id"], puestoId=PUESTOS[puesto],
               empiezaEn=lima(ev["fecha"], int(desde), int(desde % 1 * 60)),
               terminaEn=lima(ev["fecha"], int(hasta), int(hasta % 1 * 60)), publicado=True)

# ─────────────────────────── 4. gastos fijos, campañas y quejas ───────────────────────────

S.fila("Gasto", id=nuevo_id(), eventoId=None, categoriaId=CAT_GASTO["APDAYC"], importeCentimos=38000,
       descripcion="Licencia APDAYC octubre", usuarioId=DIRECTOR, fechaGasto=lima(D(2026, 10, 28), 12))
S.fila("Gasto", id=nuevo_id(), eventoId=None, categoriaId=CAT_GASTO["Suministros"], importeCentimos=147800,
       descripcion="Recibo de luz (SEAL) y agua (SEDAPAR)", usuarioId=DIRECTOR, fechaGasto=lima(D(2026, 10, 18), 10))

S.antes.append("""update "Campania" set estado = 'ENVIADO', "enviadaEn" = "programadaEn", enviados = 96, aperturas = 61,
                  clics = 22 where nombre = 'Halloween VIP · reserva tu box' and estado = 'PROGRAMADO'""")
CAMPANIAS = [
    ("Cumpleañeros de noviembre", "WHATSAPP", "Cumpleaños del mes", None,
     "¡Feliz mes! En noviembre tu box y tu sellada van por la casa. Escríbenos al 986 678 040 y separa tu fecha.",
     "ENVIADO", lima(D(2026, 11, 1), 18, 20), 47, 39, 11),
    ("Te extrañamos · Guaracha Night", "CORREO", "Dormidos 60 días", "Vuelve el viernes 13 con DJ Fabri",
     "Hace tiempo que no te vemos por Av. Dolores. El viernes 13 llega DJ Fabri con guaracha y aleteo: preventa a S/ 15.",
     "PROGRAMADO", None, 0, 0, 0),
    ("Año Nuevo VIP · preventa para frecuentes", "CORREO", "VIP", "Tu sitio para recibir el 2027",
     "Antes que nadie: la preventa de Año Nuevo y los boxes del 31 ya están abiertos para clientes frecuentes.",
     "BORRADOR", None, 0, 0, 0),
]
for nombre, canal, segmento, asunto, cuerpo, estado, enviada, enviados, aperturas, clics in CAMPANIAS:
    if segmento not in SEGMENTOS:
        continue
    S.fila("Campania", id=nuevo_id(), nombre=nombre, canal=canal, segmentoId=SEGMENTOS[segmento], asunto=asunto,
           cuerpo=cuerpo, estado=estado,
           programadaEn=lima(D(2026, 11, 9), 19) if estado == "PROGRAMADO" else enviada, enviadaEn=enviada,
           enviados=enviados, aperturas=aperturas, clics=clics, ejecucionId=None,
           creadoEn=(enviada or AHORA) - dt.timedelta(days=1, hours=R.randint(1, 6)))

QUEJAS = [
    ("QUEJA", "INSTAGRAM", "Se quejó de que en Halloween no dejaron entrar a su grupo a las 2 a. m. por aforo completo."),
    ("QUEJA", "WHATSAPP", "Reclamó la cola de la barra en la barra libre del sábado 10."),
    ("NOTA", "WHATSAPP", "Preguntó si hay preventa para Año Nuevo y si incluye cotillón."),
    ("NOTA", "TELEFONO", "Quiere el Reservado 1 para su cumpleaños el 14 de noviembre."),
    ("QUEJA", "WHATSAPP", "Pagó por Yape en la puerta y no le llegó el QR al correo; se reenvió desde el panel."),
    ("NOTA", "EN_PERSONA", "Pidió que vuelva Kandela Crew; dice que trae a su grupo de salsa."),
]
frecuentes = [c for c in sorted(CLIENTES.values(), key=lambda c: -c["visitas"])[:150] if c["ultima"]]
for tipo, canal, resumen in QUEJAS:
    c = R.choice(frecuentes)
    cuando = min(c["ultima"] + dt.timedelta(hours=R.randint(10, 60)), AHORA - dt.timedelta(hours=R.randint(1, 30)))
    S.fila("Interaccion", id=nuevo_id(), clienteId=c["id"], tipo=tipo, canal=canal, resumen=resumen, metadatos=None,
           ocurrioEn=cuando)

# ─────────────────────────── 5. al final: productos y fichas ───────────────────────────

for sku, p in PRODUCTOS.items():
    if p["nuevo"]:
        S.fila("Producto", id=p["id"], sku=sku, nombre=p["nombre"], categoriaId=p["categoria"], unidad=p["unidad"],
               costeCentimos=p["coste"], precioCentimos=p["precio"], alertaStock=p["alerta"], activo=True, stock=stock[sku])
    else:
        S.despues.append(f'update "Producto" set stock = {stock[sku]} where id = {q(p["id"])}')

for c in CLIENTES.values():
    if c["tocado"]:
        S.despues.append(f'update "Cliente" set visitas = {c["visitas"]}, "gastoTotalCentimos" = {c["gasto"]}, '
                         f'"ultimaVisitaEn" = {q(c["ultima"])} where id = {q(c["id"])}')

ORDEN = ["Producto", "Evento", "TipoEntrada", "EventoArtista", "Cliente", "Pedido", "LineaPedido", "Entrada", "Acceso",
         "MovimientoFidelidad", "Interaccion", "OrdenCompra", "LineaOrdenCompra", "SesionCaja", "VentaBarra",
         "LineaVentaBarra", "MovimientoStock", "Turno", "Gasto", "IncidenciaNoche", "ReservaBox", "ListaInvitados",
         "Invitado", "Campania"]
ruta = os.path.join(RAIZ, "poblar-noviembre.sql")
S.escribir(ruta, ORDEN)

print(f"{'fecha':<11} {'noche':<52} {'obj':>4} {'entr':>4}")
for ev in PASADAS:
    print(f"{ev['fecha']} {ev['titulo'][:52]:<52} {ev['objetivo']:>4} {ev['asistentes']:>4}")
for ev in FUTURAS:
    print(f"{ev['fecha']} {ev['titulo'][:52]:<52} {ev['estado']:>9} vendidas {sum(t['vendidas'] for t in ev['tipos'])}")
for tabla in ORDEN:
    if S.filas.get(tabla):
        print(f"  {tabla}: {len(S.filas[tabla])}")
print("stock final:", {s: stock[s] for s in sorted(stock)})
print("por debajo del mínimo:", [(s, stock[s], PRODUCTOS[s]["alerta"]) for s in PRODUCTOS if stock[s] < PRODUCTOS[s]["alerta"]])
