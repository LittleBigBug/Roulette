package me.matsubara.roulette.util;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.cryptomorin.xseries.ReflectionUtils;
import com.cryptomorin.xseries.XMaterial;
import com.google.common.base.Preconditions;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.npc.modifier.MetadataModifier;
import me.matsubara.roulette.npc.modifier.NPCModifier;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang3.Validate;
import org.bukkit.Color;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils {

    private static final RoulettePlugin PLUGIN = JavaPlugin.getPlugin(RoulettePlugin.class);

    private static final Pattern PATTERN = Pattern.compile("&#([\\da-fA-F]{6})");

    private static final BlockFace[] AXIS = {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST};

    private static final BlockFace[] RADIAL = {
            BlockFace.NORTH,
            BlockFace.NORTH_EAST,
            BlockFace.EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH,
            BlockFace.SOUTH_WEST,
            BlockFace.WEST,
            BlockFace.NORTH_WEST};

    // Pose enum objects.
    private static Object SNEAKING;
    private static Object STANDING;

    public static final Color[] COLORS = getColors();

    private static final MethodHandle SET_PROFILE;
    private static final MethodHandle PROFILE;

    private static Color @NotNull [] getColors() {
        Field[] fields = Color.class.getDeclaredFields();

        List<Color> results = new ArrayList<>();
        for (Field field : fields) {
            if (!field.getType().equals(Color.class)) continue;

            try {
                results.add((Color) field.get(null));
            } catch (IllegalAccessException exception) {
                exception.printStackTrace();
            }
        }
        return results.toArray(new Color[0]);
    }

    static {
        if (ReflectionUtils.MINOR_NUMBER > 13) {
            Class<?> ENTITY_POSE = ReflectionUtils.getNMSClass("world.entity", "EntityPose");

            Method valueOf = null;

            try {
                //noinspection ConstantConditions
                valueOf = ENTITY_POSE.getMethod("valueOf", String.class);

                int ver = ReflectionUtils.MINOR_NUMBER;
                SNEAKING = valueOf.invoke(null, ver == 14 ? "SNEAKING" : "CROUCHING");
                STANDING = valueOf.invoke(null, "STANDING");
            } catch (IllegalArgumentException exception) {
                // The only way this exception can occur is if the server is using obfuscated code (in 1.17).
                assert valueOf != null;

                try {
                    SNEAKING = valueOf.invoke(null, "f");
                    STANDING = valueOf.invoke(null, "a");
                } catch (ReflectiveOperationException ignore) {
                }
            } catch (ReflectiveOperationException exception) {
                exception.printStackTrace();
            }
        }

        Class<?> craftMetaSkull = ReflectionUtils.getCraftClass("inventory.CraftMetaSkull");
        Preconditions.checkNotNull(craftMetaSkull);

        SET_PROFILE = Reflection.getMethod(craftMetaSkull, "setProfile", GameProfile.class);
        PROFILE = Reflection.getFieldSetter(craftMetaSkull, "profile");
    }

    /**
     * Fixed sneaking metadata that works on 1.14 too.
     */
    @SuppressWarnings("unchecked")
    public static final MetadataModifier.EntityMetadata<Boolean, Byte> SNEAKING_METADATA = new MetadataModifier.EntityMetadata<>(
            0,
            Byte.class,
            Collections.emptyList(),
            input -> (byte) (input ? 0x02 : 0),
            new MetadataModifier.EntityMetadata<>(
                    6,
                    (Class<Object>) EnumWrappers.getEntityPoseClass(),
                    Collections.emptyList(),
                    input -> (input ? SNEAKING : STANDING),
                    () -> NPCModifier.MINECRAFT_VERSION >= 14));

    public static @NotNull BlockFace getFace(float yaw, boolean subCardinal) {
        return (subCardinal ? RADIAL[Math.round(yaw / 45f) & 0x7] : AXIS[Math.round(yaw / 90f) & 0x3]).getOppositeFace();
    }

    public static @NotNull Vector getDirection(@NotNull BlockFace face) {
        int modX = face.getModX(), modY = face.getModY(), modZ = face.getModZ();
        Vector direction = new Vector(modX, modY, modZ);
        if (modX != 0 || modY != 0 || modZ != 0) direction.normalize();
        return direction;
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Vector offsetVector(@NotNull Vector vector, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(-yawDegrees), pitch = Math.toRadians(-pitchDegrees);

        double cosYaw = Math.cos(yaw), cosPitch = Math.cos(pitch);
        double sinYaw = Math.sin(yaw), sinPitch = Math.sin(pitch);

        double initialX, initialY, initialZ, x, y, z;

        initialX = vector.getX();
        initialY = vector.getY();
        x = initialX * cosPitch - initialY * sinPitch;
        y = initialX * sinPitch + initialY * cosPitch;

        initialZ = vector.getZ();
        initialX = x;
        z = initialZ * cosYaw - initialX * sinYaw;
        x = initialZ * sinYaw + initialX * cosYaw;

        return new Vector(x, y, z);
    }

    public static ItemStack createHead(String url) {
        return createHead(url, true);
    }

    public static @Nullable ItemStack createHead(String url, boolean isMCUrl) {
        ItemStack item = XMaterial.PLAYER_HEAD.parseItem();
        if (item == null) return null;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return null;

        applySkin(meta, url, isMCUrl);
        item.setItemMeta(meta);

        return item;
    }

    public static void applySkin(SkullMeta meta, String texture, boolean isUrl) {
        applySkin(meta, UUID.randomUUID(), texture, isUrl);
    }

    public static void applySkin(SkullMeta meta, UUID uuid, String texture, boolean isUrl) {
        GameProfile profile = new GameProfile(uuid, "");

        String textureValue = texture;
        if (isUrl) {
            textureValue = "http://textures.minecraft.net/texture/" + textureValue;
            byte[] encodedData = Base64.getEncoder().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", textureValue).getBytes());
            textureValue = new String(encodedData);
        }

        profile.getProperties().put("textures", new Property("textures", textureValue));

        try {
            // If the serialized profile field isn't set, ItemStack#isSimilar() and ItemStack#equals() throw an error.
            (SET_PROFILE == null ? PROFILE : SET_PROFILE).invoke(meta, profile);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static String translate(String message) {
        Validate.notNull(message, "Message can't be null.");

        if (ReflectionUtils.MINOR_NUMBER < 16) return oldTranslate(message);

        Matcher matcher = PATTERN.matcher(oldTranslate(message));
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(builder, ChatColor.of("#" + matcher.group(1)).toString());
        }

        return matcher.appendTail(builder).toString();
    }

    @Contract("_ -> param1")
    public static @NotNull List<String> translate(List<String> messages) {
        Validate.notNull(messages, "Messages can't be null.");

        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    @Contract("_ -> new")
    private static @NotNull String oldTranslate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String getSlotName(@NotNull Slot slot) {
        if (slot.isSingleInclusive()) {
            String number = slot.isDoubleZero() ? "00" : String.valueOf(slot.getInts()[0]);
            return switch (slot.getColor()) {
                case RED -> ConfigManager.Config.SINGLE_RED.asString().replace("%number%", number);
                case BLACK -> ConfigManager.Config.SINGLE_BLACK.asString().replace("%number%", number);
                default -> ConfigManager.Config.SINGLE_ZERO.asString().replace("%number%", number);
            };
        }

        if (slot.isColumn() || slot.isDozen()) {
            return PLUGIN.getConfigManager().getColumnOrDozen(slot.isColumn() ? "column" : "dozen", slot.getColumnOrDozen());
        }

        return switch (slot) {
            case SLOT_LOW -> ConfigManager.Config.LOW.asString();
            case SLOT_EVEN -> ConfigManager.Config.EVEN.asString();
            case SLOT_ODD -> ConfigManager.Config.ODD.asString();
            case SLOT_HIGH -> ConfigManager.Config.HIGH.asString();
            case SLOT_RED -> ConfigManager.Config.RED.asString();
            default -> ConfigManager.Config.BLACK.asString();
        };
    }

    public static String format(double value) {
        return ConfigManager.Config.MONEY_ABBREVIATION_FORMAT_ENABLED.asBool() ?
                format(value, PLUGIN.getAbbreviations()) :
                PLUGIN.getEconomy().format(value);
    }

    @SuppressWarnings("IntegerDivisionInFloatingPointContext")
    public static String format(double value, NavigableMap<Long, String> lang) {
        if (value == Long.MIN_VALUE) return format(Long.MIN_VALUE + 1, lang);
        if (value < 0) return "-" + format(-value, lang);
        if (value < 1000) return PLUGIN.getEconomy().format(value);

        Map.Entry<Long, String> entry = lang.floorEntry((long) value);
        Long divideBy = entry.getKey();
        String suffix = entry.getValue();

        long truncated = (long) value / (divideBy / 10);
        boolean hasDecimal = truncated < 100 && (truncated / 10.0d) != (truncated / 10);
        return "$" + (hasDecimal ? (truncated / 10.0d) + suffix : (truncated / 10) + suffix);
    }

    public static <T extends Enum<T>> T getOrDefault(Class<T> clazz, String name, T defaultValue) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }
}