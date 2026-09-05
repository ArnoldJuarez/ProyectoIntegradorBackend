package pe.ssiomama.backend.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A quién se le compra la mercancía — tabla {@code "Supplier"}.
 *
 * <p>Solo lleva los datos de contacto. Los pedidos que se le hacen viven en
 * {@code PurchaseOrder}, que todavía no está migrado.
 */
@Entity
@Table(name = "Supplier")
public class Proveedor {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String nombre;

    /** Nombre de la persona con la que se habla, no el de la empresa. */
    @Column(name = "contact")
    private String personaDeContacto;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String telefono;

    protected Proveedor() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getPersonaDeContacto() {
        return personaDeContacto;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }
}
