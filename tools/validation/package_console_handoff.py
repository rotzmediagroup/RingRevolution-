"""Package current console art, IDs and all localized achievement copy for the owner."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import csv, io, re, xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[2]
achievements = re.findall(r'Achievement\("([a-z_]+)", (\d+), (\d+)\)', (root/'core/src/main/kotlin/com/shiostudios/dumplingrings/core/systems/Achievements.kt').read_text())
assert len(achievements) >= 25
locales = {'en':'values','es':'values-es','pt-BR':'values-pt-rBR','de':'values-de','fr':'values-fr','ja':'values-ja','ko':'values-ko','hi':'values-hi','id':'values-in','ar':'values-ar','nl':'values-nl'}
buffer = io.StringIO()
writer = csv.writer(buffer)
writer.writerow(['locale','stable_key','name','description','target','type','art_file','build_property'])
for lang, directory in locales.items():
    strings = {e.attrib['name']:(e.text or '').replace("\\'", "'") for e in ET.parse(root/'app/src/main/res'/directory/'strings.xml').getroot()}
    for key, art, target in achievements:
        writer.writerow([lang,key,strings['ach_'+key],strings['ach_'+key+'_desc'],target,'standard',key+'.png','PGS_ACHIEVEMENT_'+key.upper()])
output = root.parent/'ring-revolution-console-handoff.zip'
with ZipFile(output, 'w', ZIP_DEFLATED) as z:
    for path in (root/'docs/play-console-art').iterdir(): z.write(path, 'art/'+path.name)
    for src, dst in [('services.properties.example','services.properties.example'),('admob.properties','admob.properties'),('docs/GAME_SERVICES_SETUP.md','GAME_SERVICES_SETUP.md')]: z.write(root/src,dst)
    z.writestr('achievement-reference.csv', buffer.getvalue().encode('utf-8-sig'))
    z.writestr('README.txt', 'Ring Revolution: 25 achievements with individual custom art.\nRelease ads use live owner units; debug ads are disabled.\nRead GAME_SERVICES_SETUP.md. PGS and billing still need console configuration.\nThe CSV is a reference, not an official Play Console import format.\nPrivacy policy: https://www.shiostudios.com/privacy-policy\n')
print(f'Packaged {len(achievements)} achievements × {len(locales)} locales: {output}')
