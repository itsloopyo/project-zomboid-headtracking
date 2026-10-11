package zombie.iso.weather;

public final class ClimateManager {
    public static final int FLOAT_PRECIPITATION_INTENSITY = stub(), FLOAT_FOG_INTENSITY = stub(), FLOAT_WIND_INTENSITY = stub(), FLOAT_CLOUD_INTENSITY = stub();
    public static final int BOOL_IS_SNOW = stub();

    private static int stub() { throw new UnsupportedOperationException("reference stub"); }

    public static class ClimateFloat {
        public void setEnableAdmin(boolean enabled) { throw new UnsupportedOperationException("reference stub"); }
        public void setAdminValue(float value) { throw new UnsupportedOperationException("reference stub"); }
    }

    public static class ClimateBool {
        public void setEnableAdmin(boolean enabled) { throw new UnsupportedOperationException("reference stub"); }
        public void setAdminValue(boolean value) { throw new UnsupportedOperationException("reference stub"); }
    }

    public ClimateFloat getClimateFloat(int id) { throw new UnsupportedOperationException("reference stub"); }
    public ClimateBool getClimateBool(int id) { throw new UnsupportedOperationException("reference stub"); }
    public void resetAdmin() { throw new UnsupportedOperationException("reference stub"); }
    public ThunderStorm getThunderStorm() { throw new UnsupportedOperationException("reference stub"); }
    public static ClimateManager getInstance() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.erosion.season.ErosionSeason getSeason() { throw new UnsupportedOperationException("reference stub"); }
    public float getCloudIntensity() { throw new UnsupportedOperationException("reference stub"); }
    public float getNightStrength() { throw new UnsupportedOperationException("reference stub"); }
    public float getRainIntensity() { throw new UnsupportedOperationException("reference stub"); }
    public float getFogIntensity() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSnowing() { throw new UnsupportedOperationException("reference stub"); }
    public float getSnowIntensity() { throw new UnsupportedOperationException("reference stub"); }
    public float getSnowFracNow() { throw new UnsupportedOperationException("reference stub"); }
    public float getWindIntensity() { throw new UnsupportedOperationException("reference stub"); }
    public float getWindAngleRadians() { throw new UnsupportedOperationException("reference stub"); }
    public static double getWindTickFinal() { throw new UnsupportedOperationException("reference stub"); }
}
