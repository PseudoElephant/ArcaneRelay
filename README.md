# Arcane Relay
This is a Hytale mod that attempts to add our own take at a custom logic system.

![Connect Evrything](./art/Affinity/BlockNameCards/ConnectEverything.png)

![Build Contraptions](./art/Affinity/BlockNameCards/BuildContraptions.png)

The repository is open source so feel free to use our code or assets in any other project. We also have the `./art` directory which includes all of our raw art files. Have fun!

## Additions
![Arcane Staff](./art/Affinity/BlockNameCards/StaffAndBench.png)

This are our backbone of the mod, without them you can't do much, with them you can create and link arcane blocks to send signals between them.

---

![Arcane Button](./art/Affinity/BlockNameCards/Button.png)

The most basic logic item in our mod! It allows the player to interact with it to send/relay a signal. Use the Arcane Staff to connect it to other components and trigger them from a distance. 


---

![Arcane Relay](./art/Affinity/BlockNameCards/Relay.png) 

Similar to the button this is used to send/relay a signal to other arcane blocks. But it's also a full block, so it can be moved!

---

![Arcane Toggle Relay](./art/Affinity/BlockNameCards/ToggleRelay.png) 

Like a relay but with two states! This one is hard to explain without being technical, so here is the item description: Used to block or relay signal depending on state. When a signal is received it always toggles it's state. If active it will relay the signal, if inactive it will stop the relay. Interact with the block to toggle the starting state.

---

![Arcane Pusher](./art/Affinity/BlockNameCards/Pusher.png)

This is our own take on a Piston. It pushes the block above it in the direction specified by it's orientation, will push more blocks if there are any on the way.

---

![Arcane Rotator](./art/Affinity/BlockNameCards/Rotator.png)
This block can rotate clockwise or counterclockwise depending on it's orientation. Use it to position Pusher or other things! Might break the game a little bit, but its fun while we patch things out :D

---

![Arcane Puller](./art/Affinity/BlockNameCards/Puller.png)

This block only pulls! But it has an extended range, use it to pull back the first block it encounters within 15 blocks!

---

![Arcane Breaker](./art/Affinity/BlockNameCards/Breaker.png)

Hit blocks or players and break or kill almost everything that might stand in the way of this block!

![Pixel](./art/Affinity/BlockNameCards/Pixel.png)

 A light source block that is intended more as a surface lighting. It's got different colors!! Just crouch + interact to see them :D Place it as a wall, on the ground or ceiling. Make screens, 8 segment display or whatever else you might think off! 

![More to come](./art/Affinity/BlockNameCards/MoreToCome.png)

## Customize Your Playthrough

![Arcane Relay Settings Page](./art/Affinity/BlockNameCards/SettingsMenu.png)

Added a new command `/arcanerelay settings` to allow you to override most of the important values in our mod! 

**Important Note:** These take effect as soon as you hit the save button (_no_ reload required). So if you are a server owner make sure only the people you want to access this can access it.

## Trigger Volume Support

![Trigger Volume Support](./art/Affinity/BlockNameCards/TriggerVolumeSupport.png)

Added support for Trigger Volumes, you can now trigger Arcane Relay blocks with a Trigger Volume using the `Trigger Arcane Relay` effect on the menu. For now the interaction is limited to blocks inside the volume, but looking to expand this so that Relays can trigger volumes, and so that volumes can function as independent relays with their own connections.

## Plugin Development

### Activations

When an arcane signal reaches a block, the mod looks up which **activation** to run. Activations live under `Server/Item/Activations/` as JSON; the **filename** (without `.json`) is the activation ID (e.g. `Arcane_Relay`, `Toggle_Pusher`).

**Using existing types:** Add a JSON file and set `Type` to one of the built-in types. Example for a simple on/off relay:

```json
{
  "Type": "ToggleState",
  "OnState": "default",
  "OffState": "Off",
  "SendSignalWhen": "Off"
}
```

**Chaining activations:** Use type `Chain` and list activation IDs to run in order:

```json
{
  "Type": "Chain",
  "Activations": ["Move_Block", "Toggle_Pusher"]
}
```

**Creating a new activation type (Java):** Implement a class that extends `Activation`, register its codec in your plugin's `setup()`, then add JSON assets that use your `Type`:

```java
// In setup():
ArcaneRelayPlugin.get().getCodecRegistry(Activation.CODEC)
    .register("MyCustom", MyCustomActivation.class, MyCustomActivation.CODEC);

// Your class:
public class MyCustomActivation extends Activation {
    public static final BuilderCodec<MyCustomActivation> CODEC = BuilderCodec.builder(
            MyCustomActivation.class, MyCustomActivation::new, Activation.ABSTRACT_CODEC)
        // .appendInherited(...) for your fields, then .add() and .build()
        .build();

    @Override
    public void execute(@Nonnull ActivationContext ctx) {
        // ctx.world(), ctx.chunk(), ctx.blockX/Y/Z(), ctx.blockType(), ctx.sources()
        // Use ActivationExecutor.playEffects(), playBlockInteractionSound(), sendSignals(ctx) as needed.
    }
}
```

#### Existing activation types

| Type | Description |
|------|-------------|
| **ToggleState** | Toggles block between two states (e.g. On/Off). Options: `OnState`, `OffState`, `SendSignalWhen`, `OnEffects`, `OffEffects`. |
| **SendSignal** | Forwards the signal to connected outputs. No state change. |
| **ArcaneDischarge** | Cycles charge states; sends signal when going from fully charged to off. Options: `Changes` (state map), `MaxChargeState`, `MaxChargeStateSuffix`. |
| **MoveBlock** | Pushes blocks in the facing direction (e.g. piston). Options: `Range`, `IsWall`. |
| **ToggleDoor** | Toggles a door block in front. Options: `Horizontal`, `OpenIn`, `IsWall`. |
| **Chain** | Runs several activations in sequence. Option: `Activations` (array of activation IDs). |

#### Bindings

Bindings decide **which activation runs for which block**. They live under `Server/Item/ActivationBindings/` as JSON.

- **Bindings** – Object mapping block type key → **reference or inline activation** (like interactions: either an id or Type + params).
  - **Reference:** `{ "Id": "Arcane_Relay" }` (reference to Item/Activations asset).
  - **Inline (Type + params):** `{ "Activation": { "Type": "SendSignal" } }` or `{ "Activation": { "Type": "ToggleDoor", "OpenIn": false, "Horizontal": true } }` (same shape as `Item/Activations/*.json`).
- **Default** (optional) – Fallback activation ID when a block type is not in Bindings.

You can use **one file** with all keys, or **multiple files**; multiple files are merged and **later files (by asset id / filename) override earlier** for the same key.

Example – references only:

```json
{
  "Bindings": {
    "Pseudo_Arcane_Relay": { "Id": "Arcane_Relay" },
    "Torch": { "Id": "Toggle_Torch" }
  },
  "Default": "use_block"
}
```

Example – **inline activation** (Type + params, no separate asset):

```json
{
  "Bindings": {
    "Pseudo_Arcane_Relay": { "Id": "Arcane_Relay" },
    "Some_Custom_Block": {
      "Activation": { "Type": "SendSignal" }
    },
    "Another_Block": {
      "Activation": { "Type": "ToggleDoor", "OpenIn": false, "Horizontal": true }
    }
  },
  "Default": "use_block"
}
```

## Building the Project
The following are the necessary steps required to get the project running.

### Prerequisites
- Java 25 (or compatible)
- Gradle

### Installing and Running the Project
Once you downloaded the repository locally you can run the following command for a quick check:
```bash
.gradlew hytaleDoctor
```

This should show the location of some files and caches. Ensure this are correct. 

If you want to move the Gradle cached location you can do so by specifying a new environment variable called `GRADLE_USER_HOME` and setting it your correspending path. Remember to restart VSCode in case the registry variable value was cached.

Once you are happy with the locations run the following commands and follow the instructions given in them if necessary:
```bash
./gradlew setupHytaleDev
./gradlew runServer
```

The `setupHytaleDev` command should install all necessary dependencies to build and run the project.
Then `runServer` should bootup a locally hosted Hytale server with the mod included for testing.

If you are running into issues please reference the [HytaleTools Wiki](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki).

### If Using VSCode
We have added a `launch.json` and `tasks.json` under `/.vscode`. These let you run a debuggable version of the hytale server with the mod installed by pressing F5 or going under the `Run and Debug` tab.  