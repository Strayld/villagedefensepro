package fr.obsidianlabs.plugins.villagedefense.gameplay.provider;

import java.util.UUID;

public class SpawnResult {

    private final boolean success;
    private final UUID entityId;
    private final String errorMessage;

    private SpawnResult(boolean success, UUID entityId, String errorMessage) {
        this.success      = success;
        this.entityId     = entityId;
        this.errorMessage = errorMessage;
    }

    public static SpawnResult ok(UUID entityId)       { return new SpawnResult(true, entityId, null); }
    public static SpawnResult fail(String message)    { return new SpawnResult(false, null, message); }

    public boolean isSuccess()      { return success; }
    public UUID getEntityId()       { return entityId; }
    public String getErrorMessage() { return errorMessage; }
}
