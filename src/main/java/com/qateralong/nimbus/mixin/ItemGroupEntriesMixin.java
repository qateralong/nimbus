package com.qateralong.nimbus.mixin;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Creative tab entries are a LinkedList and Fabric finds the anchor with get(i): every addBefore/addAfter walks the list
// quadratically, and mods adding thousands of entries this way (Every Compat) stall world join for 10+ seconds.
@Pseudo
@Mixin(value = FabricItemGroupEntries.class, remap = false)
public abstract class ItemGroupEntriesMixin {
    @Shadow
    private static void checkStacks(Collection<ItemStack> stacks) {
    }

    @Unique
    private static boolean nimbus$insert(Predicate<ItemStack> anchor, Collection<ItemStack> stacks, List<ItemStack> list, boolean after) {
        if (list instanceof RandomAccess) {
            return false;
        }
        checkStacks(stacks);
        int index = -1;
        if (after) {
            ListIterator<ItemStack> it = list.listIterator(list.size());
            while (it.hasPrevious()) {
                if (anchor.test(it.previous())) {
                    index = it.nextIndex() + 1;
                    break;
                }
            }
        } else {
            ListIterator<ItemStack> it = list.listIterator();
            while (it.hasNext()) {
                if (anchor.test(it.next())) {
                    index = it.previousIndex();
                    break;
                }
            }
        }
        if (index < 0) {
            list.addAll(stacks);
        } else {
            list.addAll(index, stacks);
        }
        return true;
    }

    @Inject(method = "addAfter(Ljava/util/function/Predicate;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void nimbus$afterMatching(Predicate<ItemStack> anchor, Collection<ItemStack> stacks, List<ItemStack> list, CallbackInfo ci) {
        if (nimbus$insert(anchor, stacks, list, true)) {
            ci.cancel();
        }
    }

    @Inject(method = "addBefore(Ljava/util/function/Predicate;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void nimbus$beforeMatching(Predicate<ItemStack> anchor, Collection<ItemStack> stacks, List<ItemStack> list, CallbackInfo ci) {
        if (nimbus$insert(anchor, stacks, list, false)) {
            ci.cancel();
        }
    }

    @Inject(method = {"addAfter(Lnet/minecraft/class_1799;Ljava/util/Collection;Ljava/util/List;)V", "addAfter(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Ljava/util/List;)V"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void nimbus$afterStack(ItemStack anchor, Collection<ItemStack> stacks, List<ItemStack> list, CallbackInfo ci) {
        if (nimbus$insert(stack -> ItemStack.isSameItem(anchor, stack), stacks, list, true)) {
            ci.cancel();
        }
    }

    @Inject(method = {"addBefore(Lnet/minecraft/class_1799;Ljava/util/Collection;Ljava/util/List;)V", "addBefore(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Ljava/util/List;)V"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void nimbus$beforeStack(ItemStack anchor, Collection<ItemStack> stacks, List<ItemStack> list, CallbackInfo ci) {
        if (nimbus$insert(stack -> ItemStack.isSameItem(anchor, stack), stacks, list, false)) {
            ci.cancel();
        }
    }

    @Inject(method = {"addAfter(Lnet/minecraft/class_1935;Ljava/util/Collection;Ljava/util/List;)V", "addAfter(Lnet/minecraft/world/level/ItemLike;Ljava/util/Collection;Ljava/util/List;)V"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void nimbus$afterItem(ItemLike anchor, Collection<ItemStack> stacks, List<ItemStack> list, CallbackInfo ci) {
        Item item = anchor.asItem();
        if (nimbus$insert(stack -> stack.is(item), stacks, list, true)) {
            ci.cancel();
        }
    }

    @Inject(method = {"addBefore(Lnet/minecraft/class_1935;Ljava/util/Collection;Ljava/util/List;)V", "addBefore(Lnet/minecraft/world/level/ItemLike;Ljava/util/Collection;Ljava/util/List;)V"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void nimbus$beforeItem(ItemLike anchor, Collection<ItemStack> stacks, List<ItemStack> list, CallbackInfo ci) {
        Item item = anchor.asItem();
        if (nimbus$insert(stack -> stack.is(item), stacks, list, false)) {
            ci.cancel();
        }
    }
}
