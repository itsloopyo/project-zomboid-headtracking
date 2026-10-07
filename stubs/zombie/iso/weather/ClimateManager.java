package zombie.iso.weather;

public final class ClimateManager {
    public static final int FLOAT_PRECIPITATION_INTENSITY = 3, FLOAT_FOG_INTENSITY = 5, FLOAT_WIND_INTENSITY = 6, FLOAT_CLOUD_INTENSITY = 8;
    public static final int BOOL_IS_SNOW = 0;

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
