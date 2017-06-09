/**
 * This file is part of SimpleEgg, licensed under the MIT License (MIT)
 * 
 * Copyright (c) 2015 Brian Wood
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.redpanda4552.SimpleEgg.listeners;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Egg;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;

import io.github.redpanda4552.SimpleEgg.CaptureManager;
import io.github.redpanda4552.SimpleEgg.EggTracker;
import io.github.redpanda4552.SimpleEgg.EggTrackerEntry;
import io.github.redpanda4552.SimpleEgg.Main;
import io.github.redpanda4552.SimpleEgg.util.LoreExtractor;
import io.github.redpanda4552.SimpleEgg.util.Text;

public class ListenerEggEvents extends AbstractListener {
	
	private EggTracker eggTracker;
	private CaptureManager captureManager;
	
	public ListenerEggEvents(Main plugin) {
		super(plugin);
		eggTracker = plugin.eggTracker;
		captureManager = plugin.captureManager;
	}
	
	/**
	 * By some witchcraft this event fires before PlayerEggThrowEvent.
	 * Don't ask questions, just accept that it works.
	 */
	@EventHandler
	public void onEggCollide(EntityDamageByEntityEvent event) {
		LivingEntity entity; Egg egg;
		
		if (event.getDamager() instanceof Egg) {
			egg = (Egg) event.getDamager();
		} else {
			return;
		}
		
		if (event.getEntity() instanceof LivingEntity) {
			entity = (LivingEntity) event.getEntity();
		} else {
			return;
		}
		
		eggTracker.addEntry(new EggTrackerEntry(null, entity, egg));
	}
	
	/**
	 * This fires after the damage event does, so this, somehow, works.
	 */
	@EventHandler
	public void eggCollide(PlayerEggThrowEvent event) {
		if (eggTracker.getEntry(event.getEgg()) != null) {
		    eggTracker.getEntry(event.getEgg()).setPlayer(event.getPlayer());
			event.setHatching(false);
		} else {
			return;
		}
		
		// The player is undefined before we set it above. So to make sure the
		// local var is in fact a true copy, we will define it post assignment.
		EggTrackerEntry entry = eggTracker.getEntry(event.getEgg());
		
		if (entry.getPlayer().hasPermission("SimpleEgg." + entry.getEntity().getType().toString().replaceAll("_", "").toLowerCase())) {
			if (!captureManager.ownerConfliction(entry)) {
				if (captureManager.hasCaptureMaterials(entry)) {
					captureManager.makeSpawnEgg(entry);
				} else {
					entry.getPlayer().sendMessage(Text.tag + "You need " + Text.a + plugin.consumedMaterialAmount + " " + plugin.consumedMaterialName + Text.b + " to capture a mob.");
					refundEgg(entry.getPlayer());
				}
			} else {
				entry.getPlayer().sendMessage(Text.tag + "You do not own this mob.");
				refundEgg(entry.getPlayer());
			}
		} else {
			entry.getPlayer().sendMessage(Text.tag + "You do not have permission to capture this mob type.");
			refundEgg(entry.getPlayer());
		}
		
		eggTracker.removeEntry(entry);
	}
	
	@EventHandler
	public void eggUse(PlayerInteractEvent event) {
		if (event.getItem() != null && event.getItem().getItemMeta() instanceof SpawnEggMeta && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			ItemStack stack = event.getItem();
			SpawnEggMeta meta = (SpawnEggMeta) stack.getItemMeta();
			ArrayList<String> lore = (ArrayList<String>) meta.getLore();

			// Check the first line for health, to see if we have a SimpleEgg.
			if (meta.hasLore() && lore.get(0).startsWith("Health: ")) {
				event.setCancelled(true);
				LivingEntity livingEntity = (LivingEntity) event.getPlayer().getWorld().spawnEntity(new Location(event.getPlayer().getWorld(), event.getClickedBlock().getX(), event.getClickedBlock().getY() + 1, event.getClickedBlock().getZ()), meta.getSpawnedType());
				
				if (stack.getAmount() > 1) {
					stack.setAmount(stack.getAmount() - 1);
				} else {
					event.getPlayer().getInventory().remove(stack);
				}
				
				// If the ItemStack name contains ": ", that means the mob has a custom name.
				// Below we will use ": " as a delimeter to split and remove the preceding mob type,
				// as well as the ": ", isolating the actual name.
				if (meta.getDisplayName().contains(": ")) {
				    String customName = meta.getDisplayName();
				    customName = customName.replaceFirst(meta.getDisplayName().split(": ")[0], "");
				    customName = customName.replaceFirst(": ", "");
				    livingEntity.setCustomName(customName);
				}
				
				new LoreExtractor(lore, livingEntity);
			}
		}
	}
	
	@EventHandler
	public void eggUseOnEntity(PlayerInteractEntityEvent event) {
		ItemStack stack = event.getPlayer().getInventory().getItemInMainHand();
		ItemMeta meta = stack.getItemMeta();
		
		if (meta instanceof SpawnEggMeta) {
			if (meta != null && meta.getLore() != null) {
				if (meta.getLore().size() >= 1 && meta.getLore().get(0).startsWith("Health: ")) {
					event.getPlayer().sendMessage(Text.tag + "You cannot use a SimpleEgg to make babies out of other adult mobs.");
					event.setCancelled(true);
				}
			}
		}
	}
	
	private void refundEgg(Player player) {
		if (plugin.getConfig().getBoolean("egg-refund")) {
			player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.EGG, 1));
		}
	}
}