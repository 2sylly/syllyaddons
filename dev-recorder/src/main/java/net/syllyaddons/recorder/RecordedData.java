package net.syllyaddons.recorder;

import com.wynntils.utils.mc.LoreUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.syllyaddons.recorder.mixin.AbstractContainerScreenAccessor;

final class RecordedData {
    private RecordedData() {}

    static Map<String, Object> screen(Screen screen) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("screenClass", screen.getClass().getName());
        data.put("title", screen.getTitle().getString());
        data.put("width", screen.width);
        data.put("height", screen.height);
        if (screen instanceof AbstractContainerScreen<?> container) {
            data.put("containerId", container.getMenu().containerId);
            data.put("slotCount", container.getMenu().slots.size());
        }
        return data;
    }

    static List<Map<String, Object>> items(List<ItemStack> items) {
        List<Map<String, Object>> recorded = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            if (stack.isEmpty()) continue;
            Map<String, Object> item = new LinkedHashMap<>(item(stack));
            item.put("slot", index);
            recorded.add(item);
        }
        return recorded;
    }

    static Map<String, Object> item(ItemStack stack) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (stack == null || stack.isEmpty()) {
            data.put("empty", true);
            return data;
        }
        data.put("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        data.put("count", stack.getCount());
        data.put("name", stack.getHoverName().getString());
        data.put("lore", LoreUtils.getTooltipLines(stack).stream()
                .map(component -> component.getString().strip())
                .filter(line -> !line.isEmpty())
                .toList());
        return data;
    }

    static Map<String, Object> slotAt(Screen screen, double mouseX, double mouseY) {
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !(screen instanceof AbstractContainerScreenAccessor position)) {
            return Map.of();
        }
        int left = position.syllyrecorder$getLeftPos();
        int top = position.syllyrecorder$getTopPos();
        for (int index = 0; index < container.getMenu().slots.size(); index++) {
            var slot = container.getMenu().slots.get(index);
            if (!slot.isActive()
                    || mouseX < left + slot.x
                    || mouseX >= left + slot.x + 16
                    || mouseY < top + slot.y
                    || mouseY >= top + slot.y + 16) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("slot", index);
            data.put("item", item(slot.getItem()));
            return data;
        }
        return Map.of();
    }
}
