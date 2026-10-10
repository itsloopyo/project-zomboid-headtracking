package zombie.iso.worldgen.biomes;

import java.util.List;
import java.util.Map;

public interface IBiome {
    Map<FeatureType, List<Feature>> getFeatures();
}
