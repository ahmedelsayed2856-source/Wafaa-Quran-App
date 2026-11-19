"""Bundle the complete Uthmani Quran from AlQuran Cloud during CI build.
The build fails if the canonical 114 surahs / 6236 ayahs are not present.
Review the source and verify the text against an approved Mushaf before publication.
"""
import json, urllib.request, pathlib
url='https://api.alquran.cloud/v1/quran/quran-uthmani'
req=urllib.request.Request(url,headers={'User-Agent':'WafaaQuranBuilder/1.0'})
with urllib.request.urlopen(req,timeout=60) as response:
    raw=json.load(response)
if raw.get('code')!=200: raise ValueError('Quran source unavailable')
surahs=raw['data']['surahs']
if len(surahs)!=114 or sum(len(s['ayahs']) for s in surahs)!=6236: raise ValueError('Quran data incomplete')
clean={'source':url,'surahs':[{'number':s['number'],'name':s['name'],'ayahs':[{'numberInSurah':a['numberInSurah'],'text':a['text']} for a in s['ayahs']]} for s in surahs]}
out=pathlib.Path('app/src/main/assets/quran.json')
out.write_text(json.dumps(clean,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print('Bundled',len(surahs),'surahs and',sum(len(s['ayahs']) for s in surahs),'ayahs')
