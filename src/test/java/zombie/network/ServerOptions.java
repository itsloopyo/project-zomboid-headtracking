package zombie.network;

/** The tests' stand-in for the server's options, of which a test sets the one the mod reads. See zombie.core.properties.PropertyContainer. */
public final class ServerOptions {
    public static final class BooleanServerOption {
        private boolean value;

        public boolean getValue() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }
    }

    public BooleanServerOption pvp = new BooleanServerOption();

    private static final ServerOptions INSTANCE = new ServerOptions();

    public static ServerOptions getInstance() {
        return INSTANCE;
    }
}
