package zombie.characters.ecs;

public abstract class ECSComponent {
    public <OwnerType> OwnerType tryGetECSOwnerEntityAs(Class<? extends OwnerType> type) { throw new UnsupportedOperationException("reference stub"); }
}
