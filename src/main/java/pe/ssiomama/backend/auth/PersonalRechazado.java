package pe.ssiomama.backend.auth;

/** Un cambio en el personal que no se admite: correo repetido, quitarse el acceso a uno mismo… */
public class PersonalRechazado extends RuntimeException {

    public PersonalRechazado(String mensaje) {
        super(mensaje);
    }
}
