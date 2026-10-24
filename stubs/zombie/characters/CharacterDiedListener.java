package zombie.characters;

public interface CharacterDiedListener {
    void onDied(IsoGameCharacter character, zombie.iso.objects.IsoDeadBody body);
}
