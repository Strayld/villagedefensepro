package fr.obsidianlabs.plugins.villagedefense.compat;

import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.List;

public final class FloatingText {

    private static final double LINE_HEIGHT = 0.26D;

    private final List<ArmorStand> lines = new ArrayList<>();

    public boolean isValid() {
        return !lines.isEmpty() && lines.stream().allMatch(ArmorStand::isValid);
    }

    public void update(Location baseLocation, String text) {
        if (baseLocation.getWorld() == null) {
            remove();
            return;
        }

        List<String> renderedLines = renderLines(text);
        if (renderedLines.isEmpty()) {
            remove();
            return;
        }

        if (lines.size() != renderedLines.size() || !isValid()) {
            remove();
            spawn(baseLocation, renderedLines);
            return;
        }

        for (int i = 0; i < renderedLines.size(); i++) {
            ArmorStand stand = lines.get(i);
            stand.teleport(lineLocation(baseLocation, renderedLines.size(), i));
            stand.setCustomName(renderedLines.get(i));
        }
    }

    public void remove() {
        for (ArmorStand line : lines) {
            if (line != null && line.isValid()) {
                line.remove();
            }
        }
        lines.clear();
    }

    private void spawn(Location baseLocation, List<String> renderedLines) {
        for (int i = 0; i < renderedLines.size(); i++) {
            String lineText = renderedLines.get(i);
            Location lineLocation = lineLocation(baseLocation, renderedLines.size(), i);
            ArmorStand armorStand = lineLocation.getWorld().spawn(lineLocation, ArmorStand.class, stand -> {
                stand.setMarker(true);
                stand.setVisible(false);
                stand.setSmall(true);
                stand.setGravity(false);
                stand.setSilent(true);
                stand.setPersistent(false);
                stand.setInvulnerable(true);
                stand.setCustomNameVisible(true);
                stand.setCustomName(lineText);
            });
            lines.add(armorStand);
        }
    }

    private Location lineLocation(Location baseLocation, int lineCount, int index) {
        double offset = ((lineCount - 1) * LINE_HEIGHT / 2.0D) - (index * LINE_HEIGHT);
        return baseLocation.clone().add(0.0D, offset, 0.0D);
    }

    private List<String> renderLines(String text) {
        String[] split = text.split("\\n", -1);
        List<String> rendered = new ArrayList<>();
        for (String line : split) {
            if (!line.isEmpty()) {
                rendered.add(ChatUtil.color(line));
            }
        }
        return rendered;
    }
}
