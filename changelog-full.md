# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## v1.3.6 - Oct 12, 2026
- Update to work with Hytale v0.7.0.
- No user facing changes, just updated codebase to fix breaking changes introduced during the v0.7.0 upgrade. Now using some newer functionality and lots of codebase cleanup in progress.

## v1.3.5 - Aug 31, 2026
- Changed selection system so that connections are visible at all times while a relay is selected, should make it easier to debug contraptions.
- Added multiselect capability, you can now crouch click on multiple relays to select them at the same time. This way you can see their outputs.
- Changed toast notification messages to have shorter messages and use more specific mod related terminology.
- Changed the Arcane Staff Legend to better instruct on the new selection and multi-selection system.
- Visuals will now hide when not holding the Arcane Staff.

## v1.3.4 - Aug 27, 2026
- Updated to Hytale 0.6.0
- Added a Blockmovement_Test instance world. Technically not useful for the player... but feel free to check it out.
- Removed no longer used HUD logic.
- Restructured whole file layout on repo.
- Refactored some files and code.
- Removed some dead code.
- Removed some fully deprecated code and starting to remove soon to be deprecated code.
- Removed duplicates entries from the movable blocks setting on the settings page.

## v1.3.3 - Jul 21, 2026
- Added new Pixel block! It's a light source block that is intended more as a surface lighting. Place it as a wall, on the ground or ceiling.

Pixel Block Functionality:
- Interact regularly to toggle On/Off.
- Interact while crouching to change the color of the Pixel.
- Trigger with relay system :D

## v1.3.2 - Jun 28, 2026
- We did it, after 3 months of hard work you can finally hide the Arcane Staff Legend... sorry it took so long ;-;
- Made the Arcane Staff Legend prettier too! Added icons to it.
- Added Trigger Volume support. Massive thanks to @KiLLerBoy_001 for implementing it :D

More on Trigger Volume Support:
- Added New effect called Trigger Arcane Relay which lets you trigger the Arcane Relay blocks inside the volume. It has one additional setting you can configure called "Trigger Target" which can have one of the following values:
1. All Arcane Blocks In Volume: Will trigger all the Arcane Relay custom blocks within the volume regardless of what they are connected to.
2. All Relays In Volume: Will trigger only the Relay Artifacts (cyan gem) within the volume, regardless of what they are connected to.

## v1.3.1 - Jun 24, 2026
- Added a new settings menu! You can run /arcanerelay settings to open a settings menu and modify the values for all important variables in the mod.
- Added mod menu icon.
- Also included a performance optimization which should hopefully make Arcane Relay faster.

With the settings Menu you can now modify the following:
- Max Relay Distance: controls how far you can connect things using the Arcane Staff, set it to a high number for unlimited range :D
- Max Pusher Range: Controls how many consecutive blocks a Pusher can successfully push.
- Max Puller Range: Controls how far the puller extends before it starts to come back.
- Breaker Entity Damage: The damage done to entities by the Breaker
- Breaker Block Damage: A multiplier to the damage done to blocks by the breaker. Example, if set to 2 it will do twice as much damage, if set to 0.5 it will do half as much.
- Non-Movable Blocks: list of blocks that the move system should not be able to move. It checks if the string is contained in the ID of the block. So for example if you want all stones to not be movable, it suffices to write "stone".
- Non-Rotatable Blocks: List of blocks that should not be rotatable via the rotator. Checked the same as above.

## v1.3.0 - May 26, 2026
- Updated support for Hytale v0.5.1.
- Added the Rotator block, allowing you to rotate blocks and pushers into orientations not normally allowed by Hytale.
- Added the Breaker block, a new type of magic block that breaks blocks and strikes entities.
- Updated all Arcane Relay items with new quality settings and categories.
- Migrated math logic to align with the new Hytale version.
- Performed internal refactoring and backend optimizations.

## v1.2.0 - Apr 5, 2026
- Added new wireframe outlines to selections, allowing you to see the full outline of the source block and the corner outlines of all destination points when creating connections.
- Added multiple selection colors that loop as you select new sources to help identify which outputs belong to which source block.
- Updated selections to adapt to the shape of the targeted output destination block, pointing at the root block for multiblocks larger than 1x1x1.
- Added the Antiquarian's Workbench and removed its dependency on the Arcane Workbench so it opens as expected.
- Migrated existing recipes over to the Antiquarian's Workbench without changing their ingredients.
- Introduced Relay Artifacts (cyan) and Vector Artifacts (green), with plans for additional colors in future updates.
- Changed the color of the Pusher and Puller to green to reflect their status as Vector Artifacts.
- Added a new crafting recipe for green crystals.

## v1.1.1 - Mar 26, 2026
- Updated Arcane Relay to work with Hytale Release v2026.03.26-89796e57b
- Fixed all compatability issues with new Hytale Release.
- Changed recipes to use new Crystal Shards resource instead of previous custom one.
- Refactored codebase to fix use of newly deprecated methods in the plugin.
- We are now hiding the Pusher Wall Variant and Puller Extension blocks from the creative menu.
- Removed discharge block from the mod as it was not used and it no longer aligns with the vision.

## v1.1.0 - Mar 4, 2026
- Added New Block: The Puller! Can be used to pull blocks from far away, or to create chain floors that can be moved back! We think there are a lot of possibilities with this new addition and we can't wait to see what you do with it! It works the same way as all the other Arcane Relay blocks, just interact with it, or hook it up to a Button on Relay!
- Fixed Pusher gem from not always being lit.
- Reduced the cost of recipes as we heard they were too expensive. Sorry :3
- Improved the block movement system to use the regular ticking system. Can now be change when changing world tick rate.

## v1.0.6 - Feb 20, 2026
- Added support for MultiHUD. Note: It's not required as a dependency, but game will no longer crash when using other mods that do depend on MultiHUD.

## v1.0.5 - Feb 19, 2026
- Updated to support new Hytale Release Version: 2026.02.19-1a311a592
- Fixed 'Any Crystal' from not having a proper translation in the Arcanist's Bench

## v1.0.4 - Feb 18, 2026
- Added support for Hytale Release Version: 2026.02.18-f3b8fff95
- Fixed crash when player was pushed by Pusher when activated manually.
- Fixed trapdoors from not working with interaction system.

## v1.0.3 - Feb 17, 2026
Made the following changes:
- Updated the mod supported version to Hytale Release 2026.02.17-255364b8e.
- Updated Pusher placement settings for a more intuitive use. Massive thanks to @KiLLerBoy_001 for providing us with the suggested improvements.
- Fixed all light interactions with the Arcane Relay system.
- Made the Arcane Staff target more precise.
- Made the Arcane Staff not hit entities or blocks when in survival.
- Added special SFXs to Arcane Staff when selecting and adding sources. Big thanks to @CouldBeKami for suggesting this change.
- Changed all Arcane Staff Legend UI Text to be on the server.lang file.
- Changed 'removed output' text to use warning style instead of success style.
- Fixed pusher knockback when moving a player upwards (elevators should work now, sorry we previously broke them ;-;).
- Refactored the Arcane Tick System: It should now have increased performance and contraptions should now be persistent. So if a clock is running and the chunk is unloaded either by leaving the world or going very far away it will continue ticking once you come back :D

## v1.0.2 - Feb 10, 2026
- Fix crash when playing on release mode due to targetting the incorrect Hytale server version.

## v1.0.1 - Feb 9, 2026
- Fixed typo on the Arcane Staff Legend UI

## v1.0.0 - Feb 8, 2026
Added the following items and blocks:
- Arcane Staff
- Pusher Block
- Relay Block
- Toggle Relay Block
- Discharge Block (semi-functional)
- Button

Added the following systems:
- Block movement system
- Signal system for interactions

