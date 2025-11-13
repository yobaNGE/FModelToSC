Tool that allows parsing .json file of squad layer to somewhat useful SquadCalc compatable form.

How to use
Install Fmodel
Select Squad as a game
Open settings, general, enable Local Mapping File and select .usmap file from repository.
Drop mod folder to \Squad\SquadGame\Plugins\Mods\
Load mod into Fmodel
<img width="770" height="909" alt="image" src="https://github.com/user-attachments/assets/e6a30c26-45b2-44a4-8fde-b2f73a57d59c" />
Navigate to maps/gameplay_layers
<img width="639" height="368" alt="image" src="https://github.com/user-attachments/assets/d53695a8-70f5-4d74-a5eb-79476f7e24e0" />
Press RMB on layer of your interest and Save properties(.json)
<img width="413" height="395" alt="image" src="https://github.com/user-attachments/assets/0c15d465-b236-446c-8ba0-69496aac468d" />
Pass this .json as an argument to this app.
Check output folder.

I included simple server that can serve this json to squadcalc frontend. Added gorodok as an example. run using npm (?)
