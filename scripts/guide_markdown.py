"""Small dependency-free renderer for our checked-in prose/table runbooks (not general Markdown)."""
from html import escape
import re

def render_markdown(text):
    def inline(value):
        # Escape before recognizing the restricted formatting used by our runbooks.
        value=escape(value)
        value=re.sub(r'`([^`]+)`',r'<code>\1</code>',value)
        value=re.sub(r'\*\*([^*]+)\*\*',r'<strong>\1</strong>',value)
        value=re.sub(r'(https://[^\s<]+)',lambda m:'<a href="'+m[1]+'">'+m[1]+'</a>',value)
        return value
    lines=text.splitlines();out=[];i=0
    while i<len(lines):
        line=lines[i]
        if not line.strip():i+=1;continue
        if line.startswith('# '):i+=1;continue
        if line.startswith('## '):out.append('<h3>'+inline(line[3:])+'</h3>');i+=1;continue
        if line.startswith('### '):out.append('<h4>'+inline(line[4:])+'</h4>');i+=1;continue
        if line.startswith('|'):
            rows=[]
            while i<len(lines) and lines[i].startswith('|'):
                cells=[part.strip() for part in lines[i].strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?',part) for part in cells):rows.append(cells)
                i+=1
            out.append('<div class="scroll"><table><thead><tr>'+''.join('<th>'+inline(c)+'</th>' for c in rows[0])+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+inline(c)+'</td>' for c in row)+'</tr>' for row in rows[1:])+'</tbody></table></div>');continue
        if re.match(r'^(?:\d+\. |- )',line):
            numbered=line[0].isdigit();tag='ol' if numbered else 'ul';items=[]
            while i<len(lines) and re.match(r'^\d+\. ' if numbered else r'^- ',lines[i]):
                items.append(re.sub(r'^(?:\d+\. |- )','',lines[i]));i+=1
            out.append('<'+tag+'>'+''.join('<li>'+inline(c)+'</li>' for c in items)+'</'+tag+'>');continue
        paragraph=[]
        while i<len(lines) and lines[i].strip() and not re.match(r'^(?:#|\||\d+\. |- )',lines[i]):paragraph.append(lines[i]);i+=1
        if paragraph:out.append('<p>'+inline(' '.join(paragraph))+'</p>')
        else:i+=1
    return ''.join(out)
