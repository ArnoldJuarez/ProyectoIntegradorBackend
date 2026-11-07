package pe.ssiomama.backend.entradas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Vuelve a firmar el QR de cada entrada después de V6.
 *
 * <p>El código del QR es {@code id.firma}. V6 renumeró las entradas (1, 2, 3…),
 * así que los códigos guardados seguían llevando el id de texto antiguo y su
 * firma: ninguno validaría en la puerta. Esto se hace en Java y no en SQL
 * porque la firma necesita el {@code TICKET_SECRET}, que no está en la base
 * (ni debe estarlo).
 *
 * <p>Es un bean de Spring: Spring Boot entrega a Flyway las migraciones Java
 * que encuentra en el contexto, y así puede usar {@link FirmaDeEntrada} con el
 * secreto ya configurado. El nombre de la clase es el que Flyway lee como
 * versión y descripción.
 */
@Component
@SuppressWarnings("java:S101") // El nombre lo impone Flyway: V<versión>__<descripción>.
public class V7__firmar_entradas_con_id_numerico extends BaseJavaMigration {

    private static final int LOTE = 1_000;

    private final FirmaDeEntrada firma;

    public V7__firmar_entradas_con_id_numerico(FirmaDeEntrada firma) {
        this.firma = firma;
    }

    @Override
    public void migrate(Context contexto) throws Exception {
        Connection conexion = contexto.getConnection();

        List<Long> ids = new ArrayList<>();
        try (Statement st = conexion.createStatement();
                ResultSet rs = st.executeQuery("select id from \"Entrada\" order by id")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }

        try (PreparedStatement ps = conexion.prepareStatement("update \"Entrada\" set codigo = ? where id = ?")) {
            int enLote = 0;
            for (Long id : ids) {
                ps.setString(1, firma.codificar(id));
                ps.setLong(2, id);
                ps.addBatch();
                if (++enLote == LOTE) {
                    ps.executeBatch();
                    enLote = 0;
                }
            }
            if (enLote > 0) {
                ps.executeBatch();
            }
        }
    }
}
