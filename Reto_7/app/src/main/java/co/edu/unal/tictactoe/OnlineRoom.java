package co.edu.unal.tictactoe;

/** Datos de la sala; todavía no contiene jugadas ni estadísticas locales. */
public final class OnlineRoom {
    public static final String WAITING = "waiting";
    public static final String READY = "ready";
    public static final String CLOSED = "closed";

    public final String id;
    public final String host;
    public final String guest;
    public final String status;

    public OnlineRoom(String id, String host, String guest, String status) {
        this.id = id;
        this.host = host;
        this.guest = guest;
        this.status = status;
    }

    public boolean contains(String uid) {
        return uid != null && !uid.isEmpty() && (uid.equals(host) || uid.equals(guest));
    }

    public boolean canJoin(String uid) {
        return uid != null && !uid.isEmpty() && WAITING.equals(status)
                && guest.isEmpty() && !uid.equals(host);
    }

    public String displayCode() {
        return id.substring(Math.max(0, id.length() - 6));
    }
}
