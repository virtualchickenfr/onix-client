'use strict';

/*
 * The launcher in English or German. The page is written in English; in German every text on the page is swapped
 * for its translation as it appears (static HTML, everything the scripts add later, toasts, tooltips and
 * placeholders). Exact texts come from DE, texts with numbers or names in them from DE_PATTERNS.
 * Settings → Language: Automatic (the computer's language), English or Deutsch.
 * Names (profiles, mods, themes, skies, cosmetics, modules) stay as they are.
 */

const I18N = { lang: 'en', missing: new Set() };

const DE = {
  // ---- frame, navigation, home
  'Starting': 'Startet', 'Search': 'Suchen', 'Play': 'Spielen', 'Profile': 'Profil', 'Ready': 'Bereit', 'Logs': 'Logs',
  'Stop': 'Stoppen', 'Profiles': 'Profile', 'Home': 'Start', 'Discover': 'Entdecken', 'Design': 'Design', 'Pages': 'Seiten',
  'Minimize': 'Minimieren', 'Close': 'Schließen', 'Search commands': 'Befehle suchen', 'Type a command or search…': 'Befehl eingeben oder suchen…',
  'Recent': 'Zuletzt', 'Actions': 'Aktionen', 'Have fun!': 'Viel Spaß!', 'Game output': 'Spielausgabe', 'Running': 'Läuft',
  'Starting…': 'Startet…', 'Starting Minecraft': 'Minecraft startet', 'Preparing': 'Wird vorbereitet', 'Minecraft is running': 'Minecraft läuft',
  'Minecraft is already running.': 'Minecraft läuft bereits.', 'Sign in to play': 'Zum Spielen anmelden', 'Sign in': 'Anmelden',
  'Sign in with Microsoft': 'Mit Microsoft anmelden', 'Signed in': 'Angemeldet', 'Switch account': 'Konto wechseln',
  'Add another account': 'Weiteres Konto hinzufügen', 'Accounts': 'Konten', 'Not used yet': 'Noch nicht benutzt', 'Used just now': 'Gerade benutzt',
  'Demo – no Minecraft on this account': 'Demo – kein Minecraft auf diesem Konto',
  'This Microsoft account has no Minecraft – it plays the free demo': 'Dieses Microsoft-Konto hat kein Minecraft – es spielt die kostenlose Demo',
  'Checking your account': 'Konto wird geprüft', 'Loading profiles': 'Profile werden geladen', 'Looking for updates': 'Suche nach Updates',
  'Loading your profile…': 'Profil wird geladen…', 'Right Shift opens the Onix menu': 'Rechte Umschalttaste öffnet das Onix-Menü',
  'Manage profiles…': 'Profile verwalten…', 'Active profile': 'Aktives Profil', 'Active': 'Aktiv', 'Current': 'Aktuell',
  'Downloading game files': 'Spieldateien werden geladen', 'Ready – files downloaded': 'Bereit – Dateien geladen',
  'Minecraft exited with an error. See the console for details.': 'Minecraft wurde mit einem Fehler beendet. Details in der Konsole.',
  'Minecraft did not start': 'Minecraft ist nicht gestartet', 'Show the log': 'Log anzeigen', 'Play again': 'Erneut spielen', 'Try again': 'Erneut versuchen',
  // ---- profiles
  'Separate mods, worlds and settings for every version or server.': 'Eigene Mods, Welten und Einstellungen für jede Version oder jeden Server.',
  'Modpacks': 'Modpacks', 'Import Pack': 'Pack importieren', 'New Profile': 'Neues Profil', 'New profile': 'Neues Profil', 'Last played': 'Zuletzt gespielt',
  'Name': 'Name', 'Version': 'Version', 'Play time': 'Spielzeit', 'Newest': 'Neueste', 'Sort': 'Sortieren', 'All profiles': 'Alle Profile',
  'Search profiles…': 'Profile suchen…', 'Folder': 'Ordner', 'Other Version': 'Andere Version', 'Duplicate': 'Duplizieren',
  'Shortcut': 'Verknüpfung', 'Export': 'Exportieren', 'Edit': 'Bearbeiten', 'Mods': 'Mods', 'Bundles': 'Bundles',
  'Resource Packs': 'Ressourcenpakete', 'Resource packs': 'Ressourcenpakete', 'Shaders': 'Shader', 'Shader packs': 'Shaderpakete',
  'Worlds': 'Welten', 'Screenshots': 'Screenshots', 'Settings': 'Einstellungen', 'Open the profile folder': 'Profilordner öffnen',
  'Manage the profile: mods, packs, worlds, settings': 'Profil verwalten: Mods, Pakete, Welten, Einstellungen', 'Manage the profile': 'Profil verwalten',
  'Take this profile with everything in it to another Minecraft version': 'Dieses Profil mit allem darin in eine andere Minecraft-Version mitnehmen',
  'Copy this profile with its worlds and mods': 'Dieses Profil mit Welten und Mods kopieren',
  'A desktop shortcut that starts this profile': 'Eine Desktop-Verknüpfung, die dieses Profil startet', 'Shortcut created on your desktop': 'Verknüpfung auf dem Desktop erstellt',
  'PLAY TIME': 'SPIELZEIT', 'LAST PLAYED': 'ZULETZT GESPIELT', 'MEMORY': 'ARBEITSSPEICHER', 'ON DISK': 'AUF DER FESTPLATTE',
  'On disk': 'Auf der Festplatte', 'Not played yet': 'Noch nicht gespielt', 'Never': 'Nie', 'Just now': 'Gerade eben', '< 1 min': '< 1 Min.',
  'Edit Profile': 'Profil bearbeiten', 'Edit profile': 'Profil bearbeiten', 'Color': 'Farbe', 'Mod loader': 'Mod-Loader',
  'Minecraft version': 'Minecraft-Version', 'Memory': 'Arbeitsspeicher', 'Join on launch': 'Beim Start beitreten',
  'Server address (optional)': 'Serveradresse (optional)', 'Minecraft version of the new profile': 'Minecraft-Version des neuen Profils',
  'Performance pack (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, BadOptimizations, ScalableLux)': 'Performance-Paket (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, BadOptimizations, ScalableLux)',
  'Performance pack': 'Performance-Paket', 'Copy settings from the active profile': 'Einstellungen vom aktiven Profil übernehmen',
  'Delete': 'Löschen', 'Cancel': 'Abbrechen', 'Save': 'Speichern', 'Saving…': 'Speichert…', 'Please enter a name.': 'Bitte gib einen Namen ein.',
  'With Onix': 'Mit Onix', 'Without Onix': 'Ohne Onix', 'Fabric without Onix': 'Fabric ohne Onix', 'Plain Minecraft': 'Reines Minecraft',
  'Fabric API and the performance pack – the Onix mod is not available for this version yet.': 'Fabric API und das Performance-Paket – den Onix-Mod gibt es für diese Version noch nicht.',
  'Extra JVM arguments': 'Zusätzliche JVM-Argumente', 'Memory comes from the slider above; -Xmx and -Xms here are ignored.': 'Der Arbeitsspeicher kommt vom Regler oben; -Xmx und -Xms werden hier ignoriert.',
  'Java executable': 'Java-Programm', 'Window': 'Fenster', 'Start in fullscreen': 'Im Vollbild starten', 'Game': 'Spiel',
  'No server set': 'Kein Server eingestellt', 'Start the game straight into this world': 'Das Spiel direkt in dieser Welt starten',
  // profile tabs
  'Fabric mods for 1.21.1. Drop your own mods into the folder.': 'Fabric-Mods für 1.21.1. Leg eigene Mods in den Ordner.',
  'Open Folder': 'Ordner öffnen', 'Open folder': 'Ordner öffnen', 'Add Files': 'Dateien hinzufügen', 'Add file': 'Datei hinzufügen',
  'Add from Modrinth': 'Von Modrinth hinzufügen', 'Browse Modrinth': 'Modrinth durchsuchen', 'Updates': 'Updates', 'Installed': 'Installiert',
  'Check for updates': 'Nach Updates suchen', 'Required': 'Benötigt', 'Needed by Onix': 'Von Onix benötigt', 'Kept up to date by the launcher': 'Hält der Launcher aktuell',
  'Your file': 'Deine Datei', 'No mods yet': 'Noch keine Mods', 'No mods': 'Keine Mods', 'Remove': 'Entfernen', 'Remove?': 'Entfernen?',
  'Click again to move it to the trash': 'Nochmal klicken, um es in den Papierkorb zu legen', 'Click again to delete the file': 'Nochmal klicken, um die Datei zu löschen',
  'Onix, Fabric API and the performance pack are added on the first start. Add your own .jar files with "Add Files" or drop them here.': 'Onix, Fabric API und das Performance-Paket kommen beim ersten Start dazu. Eigene .jar-Dateien mit „Dateien hinzufügen“ oder hierher ziehen.',
  'Onix, Fabric API and the performance pack are added on the first launch. Find more under Browse Modrinth.': 'Onix, Fabric API und das Performance-Paket kommen beim ersten Start dazu. Mehr findest du unter „Modrinth durchsuchen“.',
  'No resource packs yet': 'Noch keine Ressourcenpakete', 'No shaders yet': 'Noch keine Shader', 'No worlds yet': 'Noch keine Welten',
  'No screenshots yet': 'Noch keine Screenshots', 'No logs yet': 'Noch keine Logs',
  'Install one from Modrinth or drop a .zip here. Switch packs on in Options › Resource Packs.': 'Installiere eins von Modrinth oder zieh eine .zip hierher. Aktivieren unter Optionen › Ressourcenpakete.',
  'Install one from Modrinth – Iris comes with it. Pick the shader in Options › Video › Shader Packs.': 'Installiere einen von Modrinth – Iris kommt mit. Shader auswählen unter Optionen › Grafik › Shaderpakete.',
  'Installs Iris and the shader pack from Modrinth and switches it on. Rain puddles, reflections and light like in the clips. Turn it off in Options › Video › Shader Packs (or press K in game).': 'Installiert Iris und das Shaderpaket von Modrinth und schaltet es ein. Pfützen, Spiegelungen und Licht wie in den Clips. Ausschalten unter Optionen › Grafik › Shaderpakete (oder K im Spiel).',
  'Install & turn on': 'Installieren & einschalten', 'Water shader:': 'Wasser-Shader:',
  'Worlds you create in this profile show up here.': 'Welten, die du in diesem Profil erstellst, erscheinen hier.',
  'Press F2 in the game – screenshots of this profile show up here.': 'Drück F2 im Spiel – Screenshots dieses Profils erscheinen hier.',
  'The game log and crash reports of this profile show up here after the first start.': 'Spiel-Log und Absturzberichte dieses Profils erscheinen hier nach dem ersten Start.',
  'Remove world': 'Welt entfernen', 'Open screenshots': 'Screenshots öffnen', 'Show in folder': 'Im Ordner zeigen', 'Open': 'Öffnen',
  'Could not read the folder': 'Ordner konnte nicht gelesen werden', 'Could not read the logs': 'Logs konnten nicht gelesen werden',
  'Could not read the screenshots': 'Screenshots konnten nicht gelesen werden', 'Could not read the worlds': 'Welten konnten nicht gelesen werden',
  'Reading…': 'Wird gelesen…', 'Reading mods…': 'Mods werden gelesen…', 'Log copied': 'Log kopiert', 'Off': 'Aus',
  'Asking Modrinth for updates…': 'Frage Modrinth nach Updates…', 'Everything is up to date': 'Alles ist aktuell', 'Nothing to update': 'Nichts zu aktualisieren',
  'Updating…': 'Wird aktualisiert…', 'Saves folder': 'Speicherordner',
  // bundles and welcome
  'Add': 'Hinzufügen', 'Adding…': 'Wird hinzugefügt…', 'Nothing new to add': 'Nichts Neues hinzuzufügen', 'Bundles could not load': 'Bundles konnten nicht geladen werden',
  'Hand-picked mod sets for this profile. One click adds every mod with what it needs; a mod that is not out for this Minecraft version yet is skipped.': 'Handverlesene Mod-Pakete für dieses Profil. Ein Klick fügt jeden Mod mit allem hinzu, was er braucht; ein Mod, den es für diese Minecraft-Version noch nicht gibt, wird übersprungen.',
  'Quality of Life': 'Komfort', 'Visuals': 'Optik', 'Sound': 'Sound', 'Extra FPS': 'Extra-FPS', 'Content Creator': 'Content Creator',
  'Small things that make everyday play nicer: inventory sorting by mouse, food stats, block info and a mod list.': 'Kleinigkeiten, die das Spielen angenehmer machen: Inventar per Maus sortieren, Hunger-Werte, Block-Infos und eine Mod-Liste.',
  'Connected glass and textures, light from torches in your hand and nicer particles. No shaders needed.': 'Verbundenes Glas und Texturen, Licht von Fackeln in der Hand und schönere Partikel. Ohne Shader.',
  'Sound that echoes in caves and footsteps that match the block you walk on.': 'Sound, der in Höhlen hallt, und Schritte, die zum Block passen, auf dem du läufst.',
  'On top of the performance pack: lower FPS in the background, fewer hidden blocks drawn, faster world loading.': 'Zusätzlich zum Performance-Paket: weniger FPS im Hintergrund, weniger verdeckte Blöcke gezeichnet, schnelleres Laden von Welten.',
  'Record your games and edit them later, with free camera paths – for videos and montages.': 'Nimm deine Runden auf und schneide sie später, mit freien Kamerafahrten – für Videos und Montagen.',
  'Welcome to Onix': 'Willkommen bei Onix', 'Skip all': 'Alles überspringen', 'Skip': 'Überspringen', 'Back': 'Zurück', 'Next': 'Weiter',
  'Let’s go': 'Los geht’s', 'Finish': 'Fertig', 'Choose a look': 'Wähle einen Look', 'Pick your mods': 'Wähle deine Mods', 'Loading bundles…': 'Bundles werden geladen…',
  'Let’s get you set up in a few clicks: sign in, choose a look and pick the mods you want. You can change all of it later.': 'In ein paar Klicks bist du startklar: anmelden, Look wählen und die Mods aussuchen, die du willst. Alles lässt sich später ändern.',
  'Sign in with the Microsoft account that owns Minecraft. You can also do this later.': 'Melde dich mit dem Microsoft-Konto an, dem Minecraft gehört. Das geht auch später.',
  'The theme colours the launcher and the menus in game.': 'Das Theme färbt den Launcher und die Menüs im Spiel.',
  'Onix and the performance pack are always included. Add more with one click:': 'Onix und das Performance-Paket sind immer dabei. Mehr mit einem Klick:',
  'Bundles need an internet connection. You find them later in every profile.': 'Bundles brauchen eine Internetverbindung. Du findest sie später in jedem Profil.',
  // other version
  'Take to Another Version': 'In eine andere Version mitnehmen', 'Makes a copy of': 'Erstellt eine Kopie von',
  'for another Minecraft version – with your worlds, Onix modules and HUD, key binds, video settings, servers, mod settings, resource and shader packs. Every mod is swapped for its version there. Your profile stays as it is.': 'für eine andere Minecraft-Version – mit deinen Welten, Onix-Modulen und HUD, Tastenbelegung, Grafikeinstellungen, Servern, Mod-Einstellungen, Ressourcen- und Shaderpaketen. Jeder Mod wird gegen seine Version dort getauscht. Dein Profil bleibt, wie es ist.',
  'Name of the copy': 'Name der Kopie', 'Create Copy': 'Kopie erstellen', 'Checking every mod on Modrinth…': 'Jeder Mod wird auf Modrinth geprüft…',
  'Could not check the mods': 'Mods konnten nicht geprüft werden', 'mods come along': 'Mods kommen mit', 'not out yet': 'noch nicht erschienen',
  'kept, switched off': 'bleiben, ausgeschaltet', 'world': 'Welt', 'worlds': 'Welten', 'copied': 'kopiert', 'packs': 'Pakete',
  'resource and shader packs': 'Ressourcen- und Shaderpakete', 'works as it is': 'läuft wie er ist', 'Modrinth not reachable': 'Modrinth nicht erreichbar',
  'Onix, Fabric API and the performance pack are set up for the new version on its own.': 'Onix, Fabric API und das Performance-Paket werden für die neue Version von selbst eingerichtet.',
  'No mods of your own in this profile.': 'Keine eigenen Mods in diesem Profil.', 'Copying…': 'Wird kopiert…',
  'Copying the profile and updating the mods…': 'Profil wird kopiert und Mods werden aktualisiert…',
  // export / import
  'Export Profile': 'Profil exportieren', 'Modrinth pack': 'Modrinth-Paket', 'CurseForge pack': 'CurseForge-Paket', 'Into another launcher': 'In einen anderen Launcher',
  '.zip · CurseForge App – every mod packed inside': '.zip · CurseForge App – jeder Mod ist darin verpackt',
  'Copies Onix, mods and settings into an installed Fabric 1.21.1 instance': 'Kopiert Onix, Mods und Einstellungen in eine installierte Fabric-1.21.1-Instanz',
  'Pack name': 'Paketname', 'Include': 'Enthalten', 'Onix Client mod': 'Onix-Client-Mod',
  'So the pack works in other launchers without installing Onix separately': 'Damit das Paket in anderen Launchern läuft, ohne Onix extra zu installieren',
  'Installed instances': 'Installierte Instanzen', 'Also copy options and server list': 'Auch Optionen und Serverliste kopieren',
  'The instance\'s own files are kept as *.before-onix': 'Die eigenen Dateien der Instanz bleiben als *.before-onix erhalten',
  'Pick an instance first': 'Wähle zuerst eine Instanz', 'Send Profile': 'Profil senden', 'Included': 'Enthalten',
  'Import': 'Importieren', 'Settings, key binds, servers, worlds, packs and mods from other clients – mods are updated to your Minecraft version.': 'Einstellungen, Tastenbelegung, Server, Welten, Pakete und Mods aus anderen Clients – Mods werden auf deine Minecraft-Version aktualisiert.',
  'Modpack File': 'Modpack-Datei', 'Choose Folder': 'Ordner wählen', 'Scan Again': 'Erneut suchen', 'Drop a folder or a modpack here': 'Zieh einen Ordner oder ein Modpack hierher',
  '.minecraft, an instance folder, .mrpack, .dawnpack or a CurseForge .zip': '.minecraft, ein Instanzordner, .mrpack, .dawnpack oder eine CurseForge-.zip',
  'Copy everything 1:1': 'Alles 1:1 kopieren', 'What to bring over': 'Was mitkommen soll', 'Into': 'Nach', 'Existing profile': 'Vorhandenes Profil',
  'Importing…': 'Wird importiert…', 'Go to Profile': 'Zum Profil', 'Import failed': 'Import fehlgeschlagen', 'Not imported:': 'Nicht importiert:',
  'Select at least one thing to import.': 'Wähle mindestens eine Sache zum Importieren.', 'No Minecraft data found in what you dropped.': 'Keine Minecraft-Daten in dem gefunden, was du hineingezogen hast.',
  'Choose a folder, or drop your .minecraft folder, an instance folder or a modpack file here.': 'Wähle einen Ordner oder zieh deinen .minecraft-Ordner, einen Instanzordner oder eine Modpack-Datei hierher.',
  'Looking for Lunar, Feather, Dawn, Badlion, LabyMod, NoRisk and launcher instances…': 'Suche nach Lunar, Feather, Dawn, Badlion, LabyMod, NoRisk und Launcher-Instanzen…',
  'Looking for other launchers…': 'Suche nach anderen Launchern…', 'Looking for running Minecraft…': 'Suche nach laufendem Minecraft…',
  'Looking for the Minecraft Launcher, Prism, MultiMC, Modrinth App, CurseForge…': 'Suche nach Minecraft Launcher, Prism, MultiMC, Modrinth App, CurseForge…',
  'Settings & key binds': 'Einstellungen & Tastenbelegung', 'Settings & keys': 'Einstellungen & Tasten', 'Server list': 'Serverliste', 'Servers': 'Server',
  'Every saved server': 'Jeder gespeicherte Server', 'Video, sound, controls': 'Grafik, Sound, Steuerung', 'Mod settings': 'Mod-Einstellungen',
  'Config files of your mods': 'Konfigurationsdateien deiner Mods', 'Fabric mods too': 'Auch Fabric-Mods', 'Forge mods': 'Forge-Mods', 'NeoForge mods': 'NeoForge-Mods',
  "Don't copy profiles": 'Keine Profile kopieren', 'Skipped mod:': 'Übersprungener Mod:', 'Unknown': 'Unbekannt', 'Folder unknown': 'Ordner unbekannt',
  'Not found': 'Nicht gefunden', 'That’s everything': 'Das ist alles', 'Modpacks become a new profile with Onix.': 'Modpacks werden ein neues Profil mit Onix.',
  // install
  'Install': 'Installieren', 'Use Onix in other launchers.': 'Onix in anderen Launchern nutzen.', 'Profiles from': 'Profile aus', 'Include Fabric API': 'Fabric API mitnehmen',
  'Running now': 'Läuft gerade', 'Other launchers': 'Andere Launcher', 'Download the mod': 'Mod herunterladen', 'With Fabric API': 'Mit Fabric API',
  'Download': 'Herunterladen', 'Put the files into the': 'Leg die Dateien in den', 'mods': 'mods',
  'folder of a Fabric installation. Right Shift opens Onix in game.': 'Ordner einer Fabric-Installation. Die rechte Umschalttaste öffnet Onix im Spiel.',
  'Installed – restart Minecraft to play with Onix': 'Installiert – starte Minecraft neu, um mit Onix zu spielen',
  'Done – pick "Onix Client" in the Minecraft Launcher': 'Fertig – wähle „Onix Client“ im Minecraft Launcher',
  'Not built yet – run "gradlew build" in the mod folder': 'Noch nicht gebaut – führe „gradlew build“ im Mod-Ordner aus', 'Installing…': 'Wird installiert…',
  // settings
  'For every profile.': 'Für jedes Profil.', 'Theme': 'Theme',
  'Themes are shared with the game: a theme you make here shows up in the Onix menu and the other way round.': 'Themes werden mit dem Spiel geteilt: ein Theme, das du hier erstellst, erscheint im Onix-Menü und umgekehrt.',
  'Background': 'Hintergrund', 'Used here and on the in-game title screen. Your own images: put PNG files into': 'Wird hier und im Titelbildschirm im Spiel benutzt. Eigene Bilder: leg PNG-Dateien in',
  'of a profile.': 'eines Profils.', 'In-game sky': 'Himmel im Spiel', 'Custom sky': 'Eigener Himmel',
  'Replaces the overworld sky. Day and night skies fade into each other with the time of day.': 'Ersetzt den Himmel der Oberwelt. Tag- und Nachthimmel gehen mit der Tageszeit ineinander über.',
  'Day sky': 'Taghimmel', 'Night sky': 'Nachthimmel', 'Same as Day': 'Wie am Tag', 'Same as day': 'Wie am Tag', 'Day': 'Tag', 'Night': 'Nacht',
  'Applies to every profile. If Minecraft is running with Onix, the new sky shows up right away. More options (brightness, rotation, fog) are in the Custom Sky module in game.': 'Gilt für jedes Profil. Läuft Minecraft mit Onix, erscheint der neue Himmel sofort. Mehr Optionen (Helligkeit, Drehung, Nebel) im Modul „Custom Sky“ im Spiel.',
  'Turn custom sky on': 'Eigenen Himmel einschalten', 'Turn custom sky off': 'Eigenen Himmel ausschalten', 'Applied in your running game': 'Im laufenden Spiel übernommen',
  'Clean layout': 'Schlichtes Layout',
  'Rounded, quiet look with the pages on a slim bar at the side and your skin on the home page. Off: the blocky Minecraft look with the hotbar.': 'Runder, ruhiger Look mit den Seiten in einer schmalen Leiste am Rand und deinem Skin auf der Startseite. Aus: der kantige Minecraft-Look mit Hotbar.',
  'Hide while playing': 'Beim Spielen ausblenden', 'Comes back as soon as Minecraft closes.': 'Kommt zurück, sobald Minecraft geschlossen wird.',
  'Fast start': 'Schnellstart', 'Reuses your sign-in for a few hours, checks mod updates twice a day and caches Java classes between starts.': 'Nutzt deine Anmeldung ein paar Stunden weiter, prüft Mod-Updates zweimal am Tag und speichert Java-Klassen zwischen den Starts.',
  'Launcher updates': 'Launcher-Updates', 'The launcher looks for a new version every time it starts.': 'Der Launcher sucht bei jedem Start nach einer neuen Version.',
  'Check now': 'Jetzt prüfen', 'Update when the PC starts': 'Beim PC-Start aktualisieren',
  'Onix updates itself quietly in the background when you start your computer and closes again – no window opens.': 'Onix aktualisiert sich still im Hintergrund, wenn du den Computer startest, und schließt sich wieder – es öffnet sich kein Fenster.',
  'Onix server': 'Onix-Server', 'Badges, coins, the cosmetics shop and gifts. Leave empty for the built-in server.': 'Abzeichen, Coins, der Cosmetics-Shop und Geschenke. Leer lassen für den eingebauten Server.',
  'Java path': 'Java-Pfad', 'Leave empty to download Java 21 automatically.': 'Leer lassen, um Java 21 automatisch herunterzuladen.', 'Automatic': 'Automatisch',
  'Folders': 'Ordner', 'Check for updates when the launcher starts': 'Beim Start des Launchers nach Updates suchen',
  'The launcher checks for updates when it starts': 'Der Launcher sucht beim Start nach Updates', 'Automatic update checks are off – use Check now': 'Automatische Update-Suche ist aus – nutze „Jetzt prüfen“',
  'Onix updates in the background when your PC starts': 'Onix aktualisiert sich beim PC-Start im Hintergrund', 'No more updates when the PC starts': 'Keine Updates mehr beim PC-Start',
  'Settings saved': 'Einstellungen gespeichert', 'Onix server cleared': 'Onix-Server zurückgesetzt', 'Onix server saved – applies in game too': 'Onix-Server gespeichert – gilt auch im Spiel',
  'The Onix server address must start with http:// or https://': 'Die Adresse des Onix-Servers muss mit http:// oder https:// beginnen',
  'Language': 'Sprache', 'Launcher, welcome steps and the Onix menus in game.': 'Launcher, Willkommens-Assistent und die Onix-Menüs im Spiel.',
  'Looking for a new version…': 'Suche nach einer neuen Version…', 'You have the newest version': 'Du hast die neueste Version',
  'Install for Next Start': 'Beim nächsten Start installieren', 'Open the download page': 'Download-Seite öffnen',
  // themes
  'Preview': 'Vorschau', 'Accent': 'Akzent', 'Buttons, switches, highlights': 'Knöpfe, Schalter, Hervorhebungen', 'Menus and windows': 'Menüs und Fenster',
  'Sky tint': 'Himmelsfarbe', 'Title screen and sky': 'Titelbildschirm und Himmel', 'Particle color': 'Partikelfarbe', 'Menu particles': 'Menü-Partikel',
  'Particle style': 'Partikelart', 'Save Theme': 'Theme speichern', 'New Theme': 'Neues Theme', 'Edit Theme': 'Theme bearbeiten', 'Edit theme': 'Theme bearbeiten',
  'Customize a copy': 'Kopie anpassen', 'My Theme': 'Mein Theme', 'Sparks': 'Funken', 'Petals': 'Blüten', 'Snow': 'Schnee', 'Embers': 'Glut', 'None': 'Keine',
  // console
  'Console': 'Konsole', 'Launcher and game output.': 'Ausgabe von Launcher und Spiel.', 'Copy': 'Kopieren', 'Clear': 'Leeren',
  'Filter by client, folder or mod…': 'Nach Client, Ordner oder Mod filtern…',
  // discover
  'Modrinth': 'Modrinth', 'Mods, resource packs, shaders and modpacks – straight into a profile.': 'Mods, Ressourcenpakete, Shader und Modpacks – direkt in ein Profil.',
  'Install into': 'Installieren in', 'Relevance': 'Relevanz', 'Downloads': 'Downloads', 'Followers': 'Follower', 'Updated': 'Aktualisiert',
  'Search Modrinth…': 'Modrinth durchsuchen…', 'Search mods…': 'Mods suchen…', 'Search modpacks…': 'Modpacks suchen…',
  'Search resource packs…': 'Ressourcenpakete suchen…', 'Search shaders…': 'Shader suchen…', 'Loading more…': 'Mehr wird geladen…',
  'Only Fabric mods for the version of the chosen profile are shown.': 'Es werden nur Fabric-Mods für die Version des gewählten Profils angezeigt.',
  'Only Iris shaders for the version of the chosen profile are shown.': 'Es werden nur Iris-Shader für die Version des gewählten Profils angezeigt.',
  'Only resource packs for the version of the chosen profile are shown.': 'Es werden nur Ressourcenpakete für die Version des gewählten Profils angezeigt.',
  'Modpacks from Modrinth': 'Modpacks von Modrinth', 'Browse': 'Durchsuchen', 'Install & turn on ': 'Installieren & einschalten',
  // skins
  'Skin & Cape': 'Skin & Umhang', 'Change your Minecraft skin and cape. Every skin you replace is kept in your library.': 'Ändere deinen Minecraft-Skin und Umhang. Jeder Skin, den du ersetzt, bleibt in deiner Bibliothek.',
  'Add skin': 'Skin hinzufügen', 'Skins': 'Skins', 'Cape': 'Umhang', 'Cape hidden': 'Umhang ausgeblendet', 'Slim': 'Schmal', 'Classic': 'Klassisch',
  'Wear': 'Anziehen', 'Rename': 'Umbenennen', 'Reset': 'Zurücksetzen', 'Skin reset': 'Skin zurückgesetzt', 'Name of the skin': 'Name des Skins',
  'Save your current skin to the library': 'Aktuellen Skin in der Bibliothek speichern', 'Your current skin is in the library': 'Dein aktueller Skin ist in der Bibliothek',
  'Skins are PNG images of 64×64 pixels.': 'Skins sind PNG-Bilder mit 64×64 Pixeln.', 'Skin added – click it to wear it': 'Skin hinzugefügt – klick ihn an, um ihn anzuziehen',
  'Sending to Mojang…': 'Wird an Mojang gesendet…', 'Remove from the library': 'Aus der Bibliothek entfernen',
  'This account has no capes yet. Capes from Minecraft events show up here automatically.': 'Dieses Konto hat noch keine Umhänge. Umhänge von Minecraft-Events erscheinen hier automatisch.',
  'Use a default skin? Your current skin is saved to your library first.': 'Einen Standard-Skin benutzen? Dein aktueller Skin wird vorher in der Bibliothek gespeichert.',
  'Mojang gives you a default character again; your skin stays in the library': 'Mojang gibt dir wieder einen Standard-Charakter; dein Skin bleibt in der Bibliothek',
  // cosmetics
  'Cosmetics': 'Cosmetics', 'Hats, capes and pets only you see – name styles other Onix players see too.': 'Hüte, Umhänge und Haustiere, die nur du siehst – Namensstile sehen auch andere Onix-Spieler.',
  'Connecting…': 'Verbinde…', 'Connecting to the Onix server…': 'Verbinde mit dem Onix-Server…', 'Random Outfit': 'Zufälliges Outfit', 'Remove All': 'Alle ablegen',
  'Drag to turn': 'Zum Drehen ziehen', 'Everything': 'Alles', 'Owned': 'Im Besitz', 'Not owned yet': 'Noch nicht im Besitz', 'All tiers': 'Alle Stufen',
  'Common': 'Gewöhnlich', 'Rare': 'Selten', 'Epic': 'Episch', 'Legendary': 'Legendär', 'Search cosmetics…': 'Cosmetics suchen…',
  '1 coin for every minute you play with Onix': '1 Coin für jede Minute, die du mit Onix spielst', '1 coin for every minute you play with Onix. Click to refresh.': '1 Coin für jede Minute, die du mit Onix spielst. Klicken zum Aktualisieren.',
  'Cosmetic': 'Cosmetic', 'Gift to (Minecraft name)': 'Verschenken an (Minecraft-Name)', 'Message (optional)': 'Nachricht (optional)', 'Gift instead': 'Stattdessen verschenken',
  'Buy': 'Kaufen', 'Buy instead': 'Stattdessen kaufen', 'Buying…': 'Wird gekauft…', 'Buy cosmetic': 'Cosmetic kaufen', 'Gift a cosmetic': 'Ein Cosmetic verschenken',
  'Send Gift': 'Geschenk senden', 'Sending…': 'Wird gesendet…', 'Free for everyone': 'Kostenlos für alle', 'Everyone owns this one.': 'Das besitzt jeder.',
  'You own this cosmetic.': 'Du besitzt dieses Cosmetic.', 'Your cosmetic': 'Dein Cosmetic', 'Take off': 'Ablegen', 'Take Off': 'Ablegen', 'Nothing equipped yet': 'Noch nichts angelegt',
  'All cosmetics removed': 'Alle Cosmetics abgelegt', 'New random outfit from your cosmetics': 'Neues zufälliges Outfit aus deinen Cosmetics', 'Unlimited coins': 'Unbegrenzte Coins',
  'The player needs to have played with Onix on this server.': 'Der Spieler muss auf diesem Server schon mit Onix gespielt haben.', 'Player name': 'Spielername',
  'Shown in the game': 'Im Spiel sichtbar', 'Hidden from the game': 'Im Spiel ausgeblendet', 'Offline': 'Offline', 'Onix active': 'Onix aktiv',
  'Head': 'Kopf', 'Face': 'Gesicht', 'Neck': 'Hals', 'Back & Wings': 'Rücken & Flügel', 'Capes': 'Umhänge', 'Pets': 'Haustiere', 'Pet': 'Haustier',
  'Auras': 'Auren', 'Aura': 'Aura', 'Trail': 'Spur', 'Name Styles': 'Namensstile', 'Name Style': 'Namensstil', 'Model': 'Modell', 'Front': 'Vorne',
  // updates
  'Updates ': 'Updates', 'You have the newest version ': 'Du hast die neueste Version',
  'Nothing to do: it installs by itself when you close the launcher. Click to install it right now.': 'Nichts zu tun: Es installiert sich von selbst, wenn du den Launcher schließt. Klick, um es jetzt gleich zu installieren.',
  // misc
  'Select': 'Auswählen', 'Done': 'Fertig', 'Size': 'Größe', 'Files': 'Dateien', 'Player': 'Spieler', 'Reload': 'Neu laden', 'Turn off': 'Ausschalten',
  'Off – run to turn on': 'Aus – ausführen zum Einschalten', 'On – run to turn off': 'An – ausführen zum Ausschalten', 'Nothing to add': 'Nichts hinzuzufügen',
  'Onix and Fabric API': 'Onix und Fabric API', 'Onix + mods': 'Onix + Mods', 'Other versions': 'Andere Versionen', 'No Fabric': 'Kein Fabric',
  'Open mods folder': 'Mods-Ordner öffnen',
  'Water': 'Wasser', 'Runner': 'Läufer', 'Meadow': 'Wiese',
  'You sink through deep, clear water; light falls in from above.': 'Du sinkst durch tiefes, klares Wasser; von oben fällt Licht herein.',
  'A dark studio with light from above – your character runs in.': 'Ein dunkles Studio mit Licht von oben – dein Charakter läuft herein.',
  'Thousands of flowers on a grey meadow. Press Play and the petals fly up.': 'Tausende Blumen auf einer grauen Wiese. Drück auf Spielen und die Blütenblätter fliegen hoch.',
  'The look of the start page, the start screen and the main menu in game.': 'Das Aussehen der Startseite, des Startbildschirms und des Hauptmenüs im Spiel.',
  'The design of your start page and of the main menu in game. You can change it any time in the settings.': 'Das Design deiner Startseite und des Hauptmenüs im Spiel. Du kannst es jederzeit in den Einstellungen ändern.',
  'Client': 'Client',

  'Every mod in exactly your version (also Sodium, Iris and Fabric API), all resource and shader packs, settings, key binds, servers, worlds and mod settings – just like in Dawn.': 'Jeder Mod in genau deiner Version (auch Sodium, Iris und Fabric API), alle Ressourcen- und Shaderpakete, Einstellungen, Tastenbelegung, Server, Welten und Mod-Einstellungen – genau wie in Dawn.',
  'move': 'bewegen', 'run': 'ausführen', 'close': 'schließen', 'anywhere': 'überall', 'Drop to add mods': 'Ablegen, um Mods hinzuzufügen',
  'done': 'fertig', 'with Onix': 'mit Onix', 'installed': 'installiert', 'Downloading Minecraft (assets)': 'Minecraft wird geladen (Assets)',
  'Only with a Microsoft account': 'Nur mit einem Microsoft-Konto', 'Skins and capes belong to your Minecraft account. Sign in first.': 'Skins und Umhänge gehören zu deinem Minecraft-Konto. Melde dich zuerst an.',
  'Sign in with Microsoft first.': 'Melde dich zuerst mit Microsoft an.', 'No other launchers found': 'Keine anderen Launcher gefunden',
  'Download the mod below and put it into the mods folder yourself.': 'Lade den Mod unten herunter und leg ihn selbst in den mods-Ordner.',
  'No Minecraft running': 'Kein Minecraft läuft', 'Start Minecraft from any launcher – it shows up here within a few seconds.': 'Starte Minecraft aus einem beliebigen Launcher – es erscheint hier nach ein paar Sekunden.',
  'Add your own mod files (.jar)': 'Eigene Mod-Dateien hinzufügen (.jar)',
  'Shaders on strong PCs (Iris + Complementary Reimagined; the Onix module Shaders switches them)': 'Shader auf starken PCs (Iris + Complementary Reimagined; das Onix-Modul „Shaders“ schaltet sie)',
  'Auto': 'Auto', 'pixels – empty for the default': 'Pixel – leer für den Standard', 'Automatic (Java is downloaded for this version)': 'Automatisch (Java wird für diese Version geladen)',
  'Applies to every profile. If Minecraft is running with Onix, the new sky shows up right away. More options (brightness, rotation, fog) are in the Custom Sky module in game.': 'Gilt für jedes Profil. Läuft Minecraft mit Onix, erscheint der neue Himmel sofort. Mehr Optionen (Helligkeit, Drehung, Nebel) im Modul „Custom Sky“ im Spiel.',
 'Open profile folder': 'Profilordner öffnen', 'Overlays': 'Overlays',
};

/** Texts with numbers or names in them. */
const DE_PATTERNS = [
  [/^(\d+) mods? added$/, (m, n) => `${n} ${n === '1' ? 'Mod' : 'Mods'} hinzugefügt`],
  [/^(\d+) (mod|mods)$/, (m, n) => `${n} ${n === '1' ? 'Mod' : 'Mods'}`],
  [/^(\d+) updated$/, '$1 aktualisiert'], [/^(\d+) unchanged$/, '$1 unverändert'], [/^(\d+) switched off$/, '$1 ausgeschaltet'],
  [/^(\d+) updated · (\d+) unchanged$/, '$1 aktualisiert · $2 unverändert'],
  [/^Getting "(.+)" ready$/, '„$1“ wird vorbereitet'], [/^Downloading (.+)$/, '$1 wird geladen'],
  [/^v([\d.]+) · Updates are only checked in the installed launcher\.$/, 'v$1 · Updates werden nur im installierten Launcher geprüft.'],
  [/^Open (.+)$/, '$1 öffnen'], [/^by (.+)$/, 'von $1'], [/^Load more \(([\d.,]+) left\)$/, 'Mehr laden ($1 übrig)'],
  [/^([\d.,]+) items$/, '$1 Artikel'], [/^(\d+) of (\d+) sources$/, '$1 von $2 Quellen'], [/^e\.g\. (.+)$/, 'z. B. $1'],
  [/^Name styles are visible to other Onix players · profile "(.+)"$/, 'Namensstile sehen auch andere Onix-Spieler · Profil „$1“'],
  [/^(.+) is now your design – here and in game$/, (m, n) => `${tr(n)} ist jetzt dein Design – hier und im Spiel`], [/^Design: (.+)$/, (m, n) => `Design: ${tr(n)}`],
  [/^Create Copy for (.+)$/, 'Kopie für $1 erstellen'], [/^Ready for (.+?) · (.*)$/, (m, v, rest) => `Bereit für ${v} · ${tr(rest.split(' · ').map(tr).join(' · '))}`],
  [/^not out for (.+) yet – switched off$/, 'für $1 noch nicht erschienen – ausgeschaltet'],
  [/^(.+) is older than (.+): worlds that were opened in a newer version may not load\. They stay safe in your original profile\.$/, '$1 ist älter als $2: Welten, die schon in einer neueren Version geöffnet wurden, laden eventuell nicht. Im Originalprofil bleiben sie sicher.'],
  [/^(\d+) h ago$/, 'vor $1 Std.'], [/^(\d+) min ago$/, 'vor $1 Min.'], [/^(\d+) d ago$/, 'vor $1 T.'], [/^(\d+) min$/, '$1 Min.'], [/^(\d+) h (\d+) min$/, '$1 Std. $2 Min.'],
  [/^played (\d+) h ago$/, 'vor $1 Std. gespielt'], [/^played (\d+) days? ago$/, 'vor $1 T. gespielt'], [/^played (\d+) months ago$/, 'vor $1 Monaten gespielt'],
  [/^Used (\d+) min ago$/, 'Vor $1 Min. benutzt'], [/^Used (\d+) h ago$/, 'Vor $1 Std. benutzt'], [/^Used (\d+) days? ago$/, 'Vor $1 T. benutzt'],
  [/^Signed in as (.+)\.$/, 'Angemeldet als $1.'], [/^Welcome, (.+)$/, 'Willkommen, $1'],
  [/^(\d+) accounts? (?:have )?signed in$/, '$1 Konten angemeldet'],
  [/^(.+) removed$/, '$1 entfernt'], [/^(.+) created$/, '$1 erstellt'], [/^(.+) moved to the trash$/, '$1 in den Papierkorb gelegt'],
  [/^(.+) is now active$/, '$1 ist jetzt aktiv'], [/^(.+) is now active in the launcher and in game$/, '$1 ist jetzt im Launcher und im Spiel aktiv'],
  [/^(.+) saved – also available in game$/, '$1 gespeichert – auch im Spiel verfügbar'],
  [/^Profile "(.+)" selected$/, 'Profil „$1“ ausgewählt'], [/^Profile "(.+)"$/, 'Profil „$1“'],
  [/^Delete profile "(.+)" with all of its worlds and mods\?$/, 'Profil „$1“ mit allen Welten und Mods löschen?'],
  [/^Delete the theme "(.+)"\?$/, 'Das Theme „$1“ löschen?'],
  [/^Search Fabric (.+) mods…$/, 'Fabric-$1-Mods suchen…'], [/^For Fabric · Minecraft (.+)$/, 'Für Fabric · Minecraft $1'],
  [/^Fabric mods for (.+)\. Drop your own mods into the folder\.$/, 'Fabric-Mods für $1. Leg eigene Mods in den Ordner.'],
  [/^Copies Onix, mods and settings into an installed Fabric (.+) instance$/, 'Kopiert Onix, Mods und Einstellungen in eine installierte Fabric-$1-Instanz'],
  [/^Resource packs for Minecraft (.+) – switch them on in Options › Resource Packs$/, 'Ressourcenpakete für Minecraft $1 – aktivieren unter Optionen › Ressourcenpakete'],
  [/^Shader packs for Minecraft (.+) – Iris is added to the profile with the first one$/, 'Shaderpakete für Minecraft $1 – Iris kommt mit dem ersten ins Profil'],
  [/^(\d+) (?:update|updates) available$/, '$1 Updates verfügbar'], [/^(\d+) (?:pack|packs) added$/, '$1 Pakete hinzugefügt'],
  [/^(\d+) (?:source|sources) found$/, '$1 Quellen gefunden'], [/^(\d+) skins added$/, '$1 Skins hinzugefügt'],
  [/^Now wearing "(.+)"$/, 'Du trägst jetzt „$1“'], [/^Nothing matches "(.+)"$/, 'Nichts passt zu „$1“'],
  [/^You have (.+) coins\.$/, 'Du hast $1 Coins.'], [/^(.+) coins$/, '$1 Coins'], [/^(.+) min played$/, '$1 Min. gespielt'],
  [/^You need (.+) more coins – keep playing!$/, 'Dir fehlen $1 Coins – spiel weiter!'], [/^You need (.+) more coins – 1 coin per minute you play\.$/, 'Dir fehlen $1 Coins – 1 Coin pro gespielter Minute.'],
  [/^(.+) bought – you are wearing it now$/, '$1 gekauft – du trägst es jetzt'],
  [/^Update ?(.*) available$/, 'Update $1 verfügbar'], [/^Update ?(.*) ready$/, 'Update $1 bereit'],
  [/^Updating to (.+) · (\d+) %$/, 'Update auf $1 · $2 %'], [/^Installing (.+) – the launcher restarts$/, '$1 wird installiert – der Launcher startet neu'],
  [/^Update (.+) downloaded – it installs by itself when you close the launcher$/, 'Update $1 geladen – es installiert sich, wenn du den Launcher schließt'],
  [/^Onix mod (.+) downloaded – your next game uses it, no restart needed$/, 'Onix-Mod $1 geladen – dein nächstes Spiel nutzt ihn, kein Neustart nötig'],
  [/^You have the newest version \(v(.+)\)\. The launcher checks again every time it starts\.$/, 'Du hast die neueste Version (v$1). Der Launcher prüft bei jedem Start erneut.'],
  [/^(.+): (\d+) mods? added$/, (m, b, n) => `${b}: ${n} Mods hinzugefügt`], [/^Adding (\d+) bundles? to (.+)…$/, '$1 Bundles werden zu $2 hinzugefügt…'],
  [/^not yet for this version: (.+)$/, 'noch nicht für diese Version: $1'], [/^failed: (.+)$/, 'fehlgeschlagen: $1'],
  [/^"(.+)" is loading in Minecraft now$/, '„$1“ wird jetzt in Minecraft geladen'], [/^Done\. Everything is in "(.+)"$/, 'Fertig. Alles ist in „$1“'],
  [/^Importing (.+)…$/, '$1 wird importiert…'], [/^(.+) is off – turn it on again under Mods$/, '$1 ist aus – unter Mods wieder einschalten'],
  [/^(.+) installed$/, '$1 installiert'], [/^Gift from (.+)$/, 'Geschenk von $1'], [/^Export "(.+)"$/, '„$1“ exportieren'],
  [/^Remove (.+) from this launcher$/, '$1 aus diesem Launcher entfernen'], [/^(.+) removed from this launcher$/, '$1 aus diesem Launcher entfernt'],
];

function tr(text) {
  if (I18N.lang !== 'de' || !text) return text;
  const lead = text.match(/^\s*/)[0];
  const tail = text.match(/\s*$/)[0];
  const core = text.trim().replace(/\s+/g, ' ');
  if (!core || OUT.has(core)) return text;
  if (Object.prototype.hasOwnProperty.call(DE, core)) return lead + DE[core] + tail;
  if (/^[\d\s.,:%+→·/-]*$/.test(core)) return text;
  for (const [re, rep] of DE_PATTERNS) {
    if (re.test(core)) return remember(lead + core.replace(re, rep) + tail);
  }
  // "1.21.11 · 4 GB · Performance pack": every part on its own
  if (core.includes(' · ')) {
    const parts = core.split(' · ');
    const done = parts.map((x) => (x ? tr(x) : x));
    if (done.some((x, i) => x !== parts[i])) return remember(lead + done.join(' · ') + tail);
  }
  if (core.startsWith('· ')) {
    const rest = tr(core.slice(2));
    if (rest !== core.slice(2)) return remember(lead + '· ' + rest + tail);
  }
  if (/[a-z]{3}/.test(core)) I18N.missing.add(core);
  return text;
}

/** Texts that are already German: never looked up again. */
const OUT = new Set(Object.values(DE));
function remember(t) {
  OUT.add(t.trim().replace(/\s+/g, ' '));
  return t;
}

const SKIP = 'script, style, textarea, input, pre, code, .console, #view-console .term, [data-no-i18n], .tmain b, .pcard-name, .theme-name, .bundle-names, .port-row b';
const ATTRS = ['title', 'placeholder', 'aria-label'];

function translateNode(node) {
  if (I18N.lang !== 'de') return;
  if (node.nodeType === Node.TEXT_NODE) {
    const p = node.parentElement;
    if (!p || p.closest(SKIP)) return;
    const t = tr(node.nodeValue);
    if (t !== node.nodeValue) node.nodeValue = t;
    return;
  }
  if (node.nodeType !== Node.ELEMENT_NODE || node.closest(SKIP.replace(', input', '').replace(', textarea', ''))) return;
  for (const a of ATTRS) {
    const v = node.getAttribute && node.getAttribute(a);
    if (v) {
      const t = tr(v);
      if (t !== v) node.setAttribute(a, t);
    }
  }
  if (node.matches && node.matches(SKIP)) return;
  const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
    if (n.nodeType === Node.ELEMENT_NODE) {
      for (const a of ATTRS) {
        const v = n.getAttribute(a);
        if (v) {
          const t = tr(v);
          if (t !== v) n.setAttribute(a, t);
        }
      }
    } else {
      const p = n.parentElement;
      if (!p || p.closest(SKIP)) continue;
      const t = tr(n.nodeValue);
      if (t !== n.nodeValue) n.nodeValue = t;
    }
  }
}

function startI18n(setting) {
  const wanted = setting === 'de' || setting === 'en' ? setting : (navigator.language || '').toLowerCase().startsWith('de') ? 'de' : 'en';
  I18N.lang = wanted;
  document.documentElement.lang = wanted;
  if (wanted !== 'de') return;
  translateNode(document.body);
  new MutationObserver((list) => {
    for (const m of list) {
      if (m.type === 'characterData') translateNode(m.target);
      else if (m.type === 'attributes') translateNode(m.target);
      else m.addedNodes.forEach(translateNode);
    }
  }).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
}
