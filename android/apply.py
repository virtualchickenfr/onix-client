#!/usr/bin/env python3
"""
Builds the source tree of the Onix Android app: Zalith Launcher (GPL-3.0, https://github.com/ZalithLauncher/ZalithLauncher)
at the commit in android/zalith.commit, plus the Onix changes:

  - app name "Onix", own package id (dev.onix.android, installs next to Zalith), Onix icon and title
  - version from launcher/package.json (versionCode = major * 1000000 + minor * 1000 + patch)
  - android/overlay/ is copied over the tree (OnixSetup: the "Onix" game version and mod updates before every start,
    OnixUpdate: app updates from this repository's releases)
  - small patches that call into them (every patch must match exactly, or the build stops)

    python3 android/apply.py            -> android/build/zalith (git-ignored)
    python3 android/apply.py --version  -> prints "<versionCode> <versionName>"
"""
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(HERE, 'build', 'zalith')
UPSTREAM = 'https://github.com/ZalithLauncher/ZalithLauncher'
APP_ID = 'dev.onix.android'


def version():
    name = json.load(open(os.path.join(ROOT, 'launcher', 'package.json')))['version']
    parts = [int(p) for p in re.findall(r'\d+', name)[:3]] + [0, 0, 0]
    return parts[0] * 1000000 + parts[1] * 1000 + parts[2], name


def run(*cmd, cwd=None):
    subprocess.run(cmd, cwd=cwd, check=True)


def checkout(commit):
    if not os.path.isdir(os.path.join(OUT, '.git')):
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        run('git', 'init', '-q', OUT)
        run('git', 'remote', 'add', 'origin', UPSTREAM, cwd=OUT)
    run('git', 'fetch', '-q', '--depth', '1', 'origin', commit, cwd=OUT)
    run('git', 'checkout', '-q', '-f', 'FETCH_HEAD', cwd=OUT)
    run('git', 'clean', '-q', '-fdx', '-e', 'local.properties', cwd=OUT)


def patch(rel, pairs):
    path = os.path.join(OUT, rel)
    text = open(path, encoding='utf-8').read()
    for old, new in pairs:
        if text.count(old) != 1:
            sys.exit(f'apply.py: patch for {rel} does not match ({text.count(old)}x):\n{old}')
        text = text.replace(old, new)
    open(path, 'w', encoding='utf-8').write(text)


def overlay():
    src = os.path.join(HERE, 'overlay')
    for base, _, files in os.walk(src):
        for f in files:
            rel = os.path.relpath(os.path.join(base, f), src)
            dst = os.path.join(OUT, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(os.path.join(base, f), dst)


def icons():
    from PIL import Image, ImageDraw, ImageFont
    res = os.path.join(OUT, 'ZalithLauncher', 'src', 'main', 'res')
    icon = Image.open(os.path.join(ROOT, 'launcher', 'build', 'icon.png')).convert('RGBA')
    for dpi, size in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
        folder = os.path.join(res, f'mipmap-{dpi}')
        os.makedirs(folder, exist_ok=True)
        small = icon.resize((size, size), Image.LANCZOS)
        small.save(os.path.join(folder, 'ic_launcher.png'))
        # round icon: the same picture inside a circle
        mask = Image.new('L', (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        rnd = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        rnd.paste(small, (0, 0), mask)
        rnd.save(os.path.join(folder, 'ic_launcher_round.png'))
        # adaptive foreground: 108dp canvas, the icon in the middle 66%
        fg_size = size * 108 // 48
        inner = fg_size * 66 // 100
        fg = Image.new('RGBA', (fg_size, fg_size), (0, 0, 0, 0))
        fg.paste(icon.resize((inner, inner), Image.LANCZOS), ((fg_size - inner) // 2, (fg_size - inner) // 2))
        fg.save(os.path.join(folder, 'ic_launcher_foreground.png'))
    with open(os.path.join(res, 'drawable', 'ic_launcher_background.xml'), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                '    <solid android:color="#FF0B0B0E" />\n</shape>\n')
    # the title picture on the about page: "Onix" in the launcher's font
    title = os.path.join(res, 'drawable', 'app_name_title.png')
    w, h = Image.open(title).size
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    font_dir = os.path.join(ROOT, 'launcher', 'src', 'fonts')
    fonts = sorted(f for f in os.listdir(font_dir) if f.lower().endswith(('.ttf', '.otf'))) if os.path.isdir(font_dir) else []
    bold = [f for f in fonts if 'bold' in f.lower()] or fonts
    font = ImageFont.truetype(os.path.join(font_dir, bold[0]), int(h * 0.8)) if bold else ImageFont.load_default()
    draw = ImageDraw.Draw(img)
    box = draw.textbbox((0, 0), 'Onix', font=font)
    draw.text(((w - (box[2] - box[0])) // 2 - box[0], (h - (box[3] - box[1])) // 2 - box[1]), 'Onix', font=font, fill=(140, 140, 152, 255))  # readable in light and dark mode
    img.save(title)


# ------------------------------------------------------------------ Liquid Glass look

# frosted glass over a blurred Onix scene: translucent white surfaces, a light rim, a soft sheen at the top
GLASS_COLORS = {
    'black_or_white': '#FFFFFF', 'background_app': '#00000000',  # the blurred scene (window background) shows through
    'background_app_error': '#FF2A0F14',
    'background_overlay': '#59000000', 'background_status_bar': '#00000000', 'background_menu_top': '#1FFFFFFF',
    'background_menu_top_error': '#40FF5A5F', 'background_menu_element': '#B9A8FF', 'background_ripple_effect': '#33FFFFFF',
    'button_background': '#26FFFFFF', 'button_background_selected': '#47FFFFFF', 'dialog_background': '#E6181820',
    'menu_bar': '#1FFFFFFF', 'menu_bar_text': '#FFFFFF', 'menu_overlay': '#29FFFFFF', 'menu_progress': '#B9A8FF',
    'background_item': '#1FFFFFFF', 'background_item_selected': '#40FFFFFF', 'background_item_disabled': '#33FF6B6B',
    'background_sponsor_intermediate': '#40FFCB8B', 'background_sponsor_advanced': '#40FDA28A',
    'edit_box_fill': '#1AFFFFFF', 'edit_box_fill_selected': '#33FFFFFF', 'mod_category_fill': '#26FFFFFF',
    'text_background_fill': '#26FFFFFF', 'settings_category': '#C9BDFF', 'favorite_color': '#FF8FA3',
    'icon_color': '#FFFFFF', 'primary_text': '#FFFFFF',
}


def glass_shape(fill, radius, rim='#38FFFFFF', padding=''):
    """A glass surface: fill + light rim, and a sheen fading out from the top edge."""
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <item>\n        <shape android:shape="rectangle">\n'
            f'            <corners android:radius="{radius}" />\n'
            f'            <solid android:color="{fill}" />\n'
            f'            <stroke android:width="1dp" android:color="{rim}" />\n'
            f'{padding}'
            '        </shape>\n    </item>\n'
            '    <item>\n        <shape android:shape="rectangle">\n'
            f'            <corners android:radius="{radius}" />\n'
            '            <gradient android:angle="270" android:startColor="#1FFFFFFF" android:centerColor="#00FFFFFF" '
            'android:centerY="0.45" android:endColor="#00FFFFFF" />\n'
            '        </shape>\n    </item>\n</layer-list>\n')


def glass_button(fill, rim):
    pad = ('            <padding android:bottom="@dimen/abc_button_padding_vertical_material" '
           'android:left="@dimen/abc_button_padding_horizontal_material" '
           'android:right="@dimen/abc_button_padding_horizontal_material" '
           'android:top="@dimen/abc_button_padding_vertical_material" />\n')
    inner = glass_shape(fill, '999dp', rim, pad).split('\n', 1)[1]
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<inset xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:insetLeft="@dimen/abc_button_inset_horizontal_material" android:insetTop="@dimen/abc_button_inset_vertical_material" '
            'android:insetRight="@dimen/abc_button_inset_horizontal_material" android:insetBottom="@dimen/abc_button_inset_vertical_material">\n'
            + inner.replace('xmlns:android="http://schemas.android.com/apk/res/android"', '') + '</inset>\n')


def glass():
    from PIL import Image, ImageFilter, ImageEnhance
    res = os.path.join(OUT, 'ZalithLauncher', 'src', 'main', 'res')
    write = lambda rel, text: open(os.path.join(res, rel), 'w', encoding='utf-8').write(text)
    # colours: the same glass look in light and dark mode
    for rel in ('values/colors.xml', 'values-night/colors_night.xml'):
        path = os.path.join(res, rel)
        text = open(path, encoding='utf-8').read()
        for name, value in GLASS_COLORS.items():
            text = re.sub(rf'(<color name="{name}">)[^<]*(</color>)', rf'\g<1>{value}\g<2>', text)
        open(path, 'w', encoding='utf-8').write(text)
    # surfaces
    write('drawable/background_card.xml', glass_shape('@color/menu_overlay', '@dimen/_16sdp'))
    for name, fill in (('background_item_unselected', 'background_item'), ('background_item_selected', 'background_item_selected'),
                       ('background_item_disabled', 'background_item_disabled'), ('background_item_2_unselected', 'background_item'),
                       ('background_item_2_selected', 'background_item_selected')):
        write(f'drawable/{name}.xml', glass_shape(f'@color/{fill}', '@dimen/_12sdp', '#26FFFFFF'))
    write('drawable/button_background_unselected.xml', glass_button('@color/button_background', '#33FFFFFF'))
    write('drawable/button_background_selected.xml', glass_button('@color/button_background_selected', '#66FFFFFF'))
    write('drawable/dialog_background.xml', glass_shape('@color/dialog_background', '@dimen/_18sdp', '#40FFFFFF'))
    edit_pad = '            <padding android:bottom="@dimen/padding_input_bottom" android:top="@dimen/padding_input_top" />\n'
    write('drawable/background_edit_box_unselected.xml', glass_shape('@color/edit_box_fill', '@dimen/_10sdp', '#26FFFFFF', edit_pad))
    write('drawable/background_edit_box_selected.xml', glass_shape('@color/edit_box_fill_selected', '@dimen/_10sdp', '#80C9BDFF'))
    # no hard drop shadows under bars on glass
    for side in ('down', 'up', 'left', 'right'):
        write(f'drawable/shadow_gradient_{side}.xml', '<?xml version="1.0" encoding="utf-8"?>\n'
              '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
              '    <solid android:color="#00000000" />\n</shape>\n')
    # the top bar: a glass strip with a light lower edge
    write('drawable/onix_glass_bar.xml', '<?xml version="1.0" encoding="utf-8"?>\n'
          '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <item><shape><solid android:color="#1FFFFFFF" /></shape></item>\n'
          '    <item android:gravity="bottom" android:height="1dp"><shape><solid android:color="#33FFFFFF" /></shape></item>\n'
          '</layer-list>\n')
    patch('ZalithLauncher/src/main/res/layout/activity_launcher.xml', [
        ('        android:layout_height="@dimen/_40sdp"\n        android:background="@color/background_menu_top"',
         '        android:layout_height="@dimen/_40sdp"\n        android:background="@drawable/onix_glass_bar"'),
    ])
    # the scene behind the glass: an Onix title background, blurred and a little darker
    scene = Image.open(os.path.join(ROOT, 'mod-1.21.11', 'src', 'main', 'resources', 'assets', 'onix', 'textures', 'gui', 'backgrounds', 'dusk.png')).convert('RGB')
    scene = scene.resize((1280, 720), Image.LANCZOS).filter(ImageFilter.GaussianBlur(16))
    scene = ImageEnhance.Brightness(scene).enhance(0.8)
    os.makedirs(os.path.join(res, 'drawable-nodpi'), exist_ok=True)
    scene.save(os.path.join(res, 'drawable-nodpi', 'onix_glass_background.jpg'), quality=88)
    patch('ZalithLauncher/src/main/java/com/movtery/zalithlauncher/feature/background/BackgroundManager.kt', [
        ('            ContextCompat.getDrawable(context, R.color.background_app)\n',
         '            ContextCompat.getDrawable(context, R.drawable.onix_glass_background)\n'),
    ])
    # dark themes everywhere and the Onix interface font (Geist, OFL)
    os.makedirs(os.path.join(res, 'font'), exist_ok=True)
    shutil.copyfile(os.path.join(ROOT, 'launcher', 'src', 'fonts', 'geist-500.ttf'), os.path.join(res, 'font', 'onix_font.ttf'))
    patch('ZalithLauncher/src/main/res/values-night/styles.xml', [
        ('<style name="AppTheme" parent="@style/Theme.AppCompat.NoActionBar">\n',
         '<style name="AppTheme" parent="@style/Theme.AppCompat.NoActionBar">\n'
         '        <item name="android:fontFamily">@font/onix_font</item>\n'
         '        <item name="fontFamily">@font/onix_font</item>\n'
         '        <item name="colorAccent">#C9BDFF</item>\n'
         '        <item name="android:windowBackground">@drawable/onix_glass_background</item>\n'),
    ])
    for rel in ('ZalithLauncher/src/main/res/values/styles.xml',):
        patch(rel, [
            ('<style name="AppTheme" parent="@style/Theme.AppCompat.DayNight.NoActionBar">\n',
             '<style name="AppTheme" parent="@style/Theme.AppCompat.NoActionBar">\n'
             '        <item name="android:fontFamily">@font/onix_font</item>\n'
             '        <item name="fontFamily">@font/onix_font</item>\n'
             '        <item name="colorAccent">#C9BDFF</item>\n'
             '        <item name="android:windowBackground">@drawable/onix_glass_background</item>\n'),
            ('<style name="CustomDialogStyle" parent="Theme.AppCompat.Light.Dialog">\n',
             '<style name="CustomDialogStyle" parent="Theme.AppCompat.Dialog">\n'
             '        <item name="android:fontFamily">@font/onix_font</item>\n'
             '        <item name="android:backgroundDimAmount">0.55</item>\n'),
            ('<style name="CustomAlertDialogTheme" parent="Theme.AppCompat.Light.Dialog.Alert">\n',
             '<style name="CustomAlertDialogTheme" parent="Theme.AppCompat.Dialog.Alert">\n'
             '        <item name="android:fontFamily">@font/onix_font</item>\n'),
        ])


def main():
    code, name = version()
    if '--version' in sys.argv:
        print(code, name)
        return
    commit = open(os.path.join(HERE, 'zalith.commit')).read().strip()
    checkout(commit)
    overlay()

    patch('ZalithLauncher/gradle.properties', [
        ('launcher_name=ZalithLauncher', 'launcher_name=Onix'),
        ('launcher_app_name=Zalith Launcher', 'launcher_app_name=Onix'),
        ('launcher_version_code=141400', f'launcher_version_code={code}'),
        ('launcher_version_name=1.4.1.4', f'launcher_version_name={name}'),
    ])
    patch('ZalithLauncher/build.gradle.kts', [
        ('        applicationId = nameId\n', f'        applicationId = "{APP_ID}"\n'),
        ('val storageProviderId = "$nameId.storage_provider"', f'val storageProviderId = "{APP_ID}.storage_provider"'),
        # release builds: the Onix key from the ONIX_KEYSTORE secret, otherwise the public debug key
        ('        create("customDebug") {',
         '        create("onixRelease") {\n'
         '            System.getenv("ONIX_KEYSTORE")?.let { storeFile = file(it) }\n'
         '            storePassword = System.getenv("ONIX_KEYSTORE_PASSWORD")\n'
         '            keyAlias = System.getenv("ONIX_KEY_ALIAS") ?: "onix"\n'
         '            keyPassword = System.getenv("ONIX_KEY_PASSWORD") ?: System.getenv("ONIX_KEYSTORE_PASSWORD")\n'
         '        }\n'
         '        create("customDebug") {'),
        ('            signingConfig = signingConfigs.getByName("releaseBuild")',
         '            signingConfig = signingConfigs.getByName(if (System.getenv("ONIX_KEYSTORE") != null) "onixRelease" else "customDebug")'),
    ])
    # the "Onix" version on the first start (instead of Zalith's notices)
    patch('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java', [
        ('        checkNotice();\n', '        com.movtery.zalithlauncher.onix.OnixSetup.ensureVersion();\n'),
    ])
    # newest Onix mod and Fabric API before every start
    patch('ZalithLauncher/src/main/java/com/movtery/zalithlauncher/launch/LaunchGame.kt', [
        ('                } else {\n'
         '                    MinecraftDownloader().start(mcVersion, versionName, listener)\n'
         '                }',
         '                } else {\n'
         '                    Thread {\n'
         '                        com.movtery.zalithlauncher.onix.OnixSetup.syncMods(version)\n'
         '                        TaskExecutors.runInUIThread { MinecraftDownloader().start(mcVersion, versionName, listener) }\n'
         '                    }.start()\n'
         '                }'),
    ])
    # app updates from the Onix releases (also the "check for updates" button in the settings)
    patch('ZalithLauncher/src/main/java/com/movtery/zalithlauncher/feature/update/UpdateUtils.kt', [
        ('            val isRelease = (ZHTools.isRelease() || ZHTools.isPreRelease()) && !ZHTools.isDebug()',
         '            val isRelease = true'),
        ('                    updateCheckerMainProgram(context, ignore)\n',
         '                    com.movtery.zalithlauncher.onix.OnixUpdate.check(context, !ignore)\n'),
    ])
    icons()
    glass()
    print(f'Onix Android {name} ({code}) ready in {OUT}')


if __name__ == '__main__':
    main()
