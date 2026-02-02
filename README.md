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