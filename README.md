# Arcane Relay
This is a Hytale mod that attempts to add our own take at a custom logic system. 

The repository is open source so feel free to use our code or assets in any other project. We also have the `./art` directory which includes all of our raw art files. Have fun!

## Additions
This mod ads a logic system to Hytale. We add the following custom blocks and items:

### Arcane Staff
This is the backbone of our mod, without it you can't do much, with it you can link arcane blocks together to send signals within them.

![Arcane Staff](./src/main/resources/Common/Icons/ItemsGenerated/Pseudo_Arcane_Staff.png)

### Pusher 
This is our own take on a Piston. It pushes the block above it in the direction specified by it's orientation, will push more blocks if there are any on the way.

![Arcane Pusher](src/main/resources/Common/Icons/ItemsGenerated/Pseudo_Arcane_Pusher.png)


### Button 
The most basic logic item in our mod! It allows the player to interact with it to send/relay a signal. Use the Arcane Staff to connect it to other components and trigger them from a distance. 

![Arcane Button](src/main/resources/Common/Icons/ItemsGenerated/Pseudo_Arcane_Button.png)

### Relay
Similar to the button this is used to send/relay a signal to other arcane blocks. But it's also a full block, so it can be moved!

![Arcane Relay](src/main/resources/Common/Icons/ItemsGenerated/Pseudo_Arcane_Relay.png)

### Toggle Relay
Like a relay but with two states! This one is hard to explain without being technical, so here is the item description: Used to block or relay signal depending on state. When a signal is received it always toggles it's state. If active it will relay the signal, if inactive it will stop the relay. Interact with the block to toggle the starting state.

![Arcane Toggle Relay](src/main/resources/Common/Icons/ItemsGenerated/Pseudo_Arcane_Toggle_Relay.png)

### Discharge
This item is kind of broken and extra complicated, and it will be sort of hidden in the mod, but you should still be able to search for it if you want to use it. This block is supposed to hold on from anywhere of 1-4 signals from different sources before discharging (sending) the signal. So once a signal is sent it will activate and store it, at which point it will only store other, different, incomming signals (not from the same source). Once it reaches the discharge amount it will

![Arcane Toggle Discharge](src/main/resources/Common/Icons/ItemsGenerated/Pseudo_Arcane_Discharge.png)


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

Bindings decide **which activation runs for which block**. They live under `Server/Item/ActivationBindings/` as JSON. Each file has:

- **Pattern** – How to match block type keys (e.g. `Hytale:Pseudo_Arcane_Relay`). Syntax: `exact:key`, `contains:sub`, `startsWith:prefix`, `endsWith:suffix`, `regex:pattern`. First match wins.
- **Activation** – Activation ID (filename of an activation under `Item/Activations/`).
- **Priority** (optional) – If `true`, this binding is checked before others.

Example: any block whose key contains `Pseudo_Arcane_Relay` uses the `Arcane_Relay` activation:

```json
{
  "Pattern": "contains:Pseudo_Arcane_Relay",
  "Activation": "Arcane_Relay",
  "Priority": true
}
```


## Building the Project 
You will need to have Maven installed on your machine. 

### Dependencies
To build the project make sure to go to `./pom.xml` and under dependencies ensure that the com.hypixel.hytale `<version>{hytale_version}</version>` has the latest release version of the game. 

Then you can run the following: 
```
mvn -U -X dependency:resolve
```

### Building and Installing
The process is a little bit manual at the moment, but you can run the following command to generate the `arcanerelay-X.Y.Z.jar` file:
```
mvn clean install
```

You will then need to move the .jar from `./tagert` to your Hytale mods folder yourself. 