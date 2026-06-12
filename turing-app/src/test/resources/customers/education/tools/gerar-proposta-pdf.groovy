// Custom Tool: gerar_proposta_pdf
//
// Gera o "Plano de Carreira em PDF" personalizado do visitante usando o
// Code Interpreter da plataforma Turing (sandbox Python). Toma os slots
// preenchidos pelo Programa-Match e renderiza um documento de 4 páginas
// com branding Executive Education, customização por área (4 paletas), career-path
// timeline visual desenhado em reportlab.graphics, e QR code do
// `share_url` pro visitante mandar pra outra pessoa direto.
//
// Por que isso é diferente:
//   1. **Tangível na mão** — usuário sai da conversa com um PDF auditável
//      pra mostrar pra cônjuge, gestor, comitê de carreira. Storyboard
//      callout: "Permite o user sair com algo nas mãos".
//   2. **Visual diferenciado por área** — paleta + accent + soft trocam
//      conforme `area` slot: financas=navy, lideranca=graphite,
//      saude=green, tecnologia=violet. Mesma identidade que o portal usa.
//   3. **Career-path timeline em vetor** — desenhado em reportlab.graphics
//      (não imagem PNG) — mantém qualidade em qualquer zoom, fonte
//      pequena legível em mobile.
//   4. **QR code do share_url** — pega o deep link de compartilhamento
//      que o flow já produz e converte em QR direto na página de
//      fechamento. Outra pessoa abrindo o QR cai num Programa-Match
//      pré-customizado.
//
// Bindings:
//   args         — tool args (vazio neste tool; tudo vem dos slots).
//   slots        — leitura dos slots da conversa atual.
//   code         — TurCustomToolCodeHelper.executePython(scriptString).
//
// Cadastrar em admin Turing > Custom Tools > Novo. returnType=markdown.
// Parameters: nenhum (a tool lê tudo dos slots).

import groovy.json.JsonOutput

// ─── Coletando os slots do Programa-Match ─────────────────────────────
def inputs = [
        name           : (slots.get("name") ?: "Visitante")?.toString(),
        cargo_atual    : (slots.get("cargo_atual") ?: "—")?.toString(),
        objetivo       : (slots.get("objetivo") ?: "—")?.toString(),
        area           : (slots.get("area") ?: "")?.toString().toLowerCase(),
        area_label     : (slots.get("area_label") ?: "Educação Executiva")?.toString(),
        final_pitch    : (slots.get("final_pitch") ?: "")?.toString(),
        stat_pitch     : (slots.get("stat_pitch") ?: "")?.toString(),
        share_url      : (slots.get("share_url") ?: "")?.toString(),
        programas_match: (slots.get("programas_match") ?: "[]")?.toString(),
        career_path    : (slots.get("career_path") ?: "[]")?.toString()
]

// Passa inputs como JSON base64-encoded. Imune a aspas, newlines, escapes,
// unicode — qualquer slot value sobrevive intacta. Python decoda na linha 1.
def inputsJson = JsonOutput.toJson(inputs)
def inputsB64 = inputsJson.getBytes("UTF-8").encodeBase64().toString()

// ─── Idempotência por hash dos inputs ──────────────────────────────────
// O Code Interpreter custa ~3s por chamada (Python startup + reportlab +
// I/O do session dir). User clicando 2× no botão "Baixar PDF" ANTES do
// download terminar gera 2 PDFs idênticos no servidor e sobreescreve a
// URL na 2ª chamada — o link antigo vira órfão (arquivo no disco sem
// referência) e a UX dá impressão de lentidão.
//
// Solução: hash MD5 do inputsJson (que já é JSON determinístico das 10
// fontes de variação) + comparação com `proposta_pdf_hash` slot. Hit →
// retorna a URL cacheada e pula o Python totalmente. Custo: ~0.2ms por
// chamada cacheada vs ~3s por chamada quente.
def inputsHash = java.security.MessageDigest.getInstance("MD5")
        .digest(inputsJson.getBytes("UTF-8"))
        .encodeHex()
        .toString()
def cachedHash = slots.get("proposta_pdf_hash")?.toString()
def cachedUrl  = slots.get("proposta_pdf_url")?.toString()
if (cachedHash == inputsHash && cachedUrl) {
    // Cache hit — mesma combinação de slots já gerou um PDF. Não roda
    // Python; mantém os slots como estão pra UI continuar exibindo o
    // botão. Não revalidamos a URL com HEAD: session dirs do code
    // interpreter sobrevivem o suficiente pra ciclo de conversa normal
    // (~10min); se o file foi limpo do disco, próximo click cai num
    // 404, persona vê erro e re-tenta — degradação aceitável vs custo
    // de HEAD em todo cache hit.
    def name = inputs.name == "Visitante" ? "" : inputs.name
    def greeting = name ? "Pronto, ${name}!" : "Pronto!"
    return "✅ ${greeting} Seu **Plano de Carreira** já está pronto — clique no botão **📄 Baixar Plano de Carreira** que apareceu abaixo dos programas (mesmo arquivo gerado anteriormente)."
}

// ─── Python script (4-page reportlab PDF) ─────────────────────────────
// Usa só reportlab — sem matplotlib, sem qrcode, sem Pillow. reportlab tem
// QR widget nativo (reportlab.graphics.barcode.qr.QrCodeWidget) e shapes
// pra desenhar a timeline. Mínimo de dependências.
def pythonScript = '''
import json, base64, datetime, os

INPUTS_B64 = "''' + inputsB64 + '''"
INPUTS = json.loads(base64.b64decode(INPUTS_B64).decode("utf-8"))

NAME        = INPUTS["name"]
CARGO       = INPUTS["cargo_atual"]
OBJETIVO    = INPUTS["objetivo"]
AREA_KEY    = INPUTS["area"]
AREA_LABEL  = INPUTS["area_label"]
FINAL_PITCH = INPUTS["final_pitch"]
STAT_PITCH  = INPUTS["stat_pitch"]
SHARE_URL   = INPUTS["share_url"]

try:
    PROGRAMS = json.loads(INPUTS["programas_match"]) or []
except Exception:
    PROGRAMS = []
try:
    CAREER = json.loads(INPUTS["career_path"]) or []
except Exception:
    CAREER = []

# ─── reportlab imports (lazy: only if installed) ──────────────────────
try:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.colors import HexColor, white, black, Color
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.units import cm, mm
    from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_JUSTIFY
    from reportlab.platypus import (
        BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer,
        PageBreak, Table, TableStyle, KeepTogether
    )
    from reportlab.platypus.flowables import Flowable
    from reportlab.graphics.shapes import Drawing, Rect, String, Circle, Line
    from reportlab.graphics.barcode.qr import QrCodeWidget
    from reportlab.graphics import renderPDF
except ImportError as e:
    print("Error: reportlab not installed. Run `pip install reportlab` in the Turing Python env.")
    print("Original ImportError:", e)
    raise SystemExit(1)

# ─── Per-area palette ────────────────────────────────────────────────
PALETTES = {
    "financas":    {"primary": HexColor("#0F2A4F"), "accent": HexColor("#3B82F6"), "soft": HexColor("#DBEAFE"), "label": "Finanças & Investimentos"},
    "lideranca":   {"primary": HexColor("#1F2937"), "accent": HexColor("#9CA3AF"), "soft": HexColor("#F3F4F6"), "label": "Liderança & Gestão"},
    "saude":       {"primary": HexColor("#065F46"), "accent": HexColor("#10B981"), "soft": HexColor("#D1FAE5"), "label": "Saúde"},
    "tecnologia":  {"primary": HexColor("#5B21B6"), "accent": HexColor("#8B5CF6"), "soft": HexColor("#EDE9FE"), "label": "Tecnologia & Dados"},
}
P = PALETTES.get(AREA_KEY, PALETTES["financas"])
BRAND_RED = HexColor("#C8102E")

# ─── Custom flowables ────────────────────────────────────────────────
class CareerTimeline(Flowable):
    """Horizontal 4-milestone timeline drawn as vector shapes — no PNG."""
    def __init__(self, milestones, width, height, palette):
        super().__init__()
        self.milestones = milestones
        self.width = width
        self.height = height
        self.palette = palette
    def draw(self):
        c = self.canv
        n = len(self.milestones) or 1
        gap = self.width / n
        baseline_y = self.height - 28
        # connecting line under all dots
        c.setStrokeColor(self.palette["accent"])
        c.setLineWidth(2)
        c.line(gap/2, baseline_y, self.width - gap/2, baseline_y)
        for i, m in enumerate(self.milestones):
            cx = gap/2 + i * gap
            # dot
            c.setFillColor(self.palette["primary"] if i in (0, n-1) else self.palette["accent"])
            c.circle(cx, baseline_y, 7, stroke=0, fill=1)
            # step label above dot
            c.setFillColor(self.palette["primary"])
            c.setFont("Helvetica-Bold", 9)
            step = (m.get("step") or "").upper()
            c.drawCentredString(cx, baseline_y + 14, step)
            # description label below dot
            c.setFont("Helvetica", 8)
            c.setFillColor(HexColor("#1F2937"))
            label = m.get("label") or ""
            if len(label) > 38:
                label = label[:35] + "..."
            c.drawCentredString(cx, baseline_y - 16, label)
            # badge under label (Programa Education / Você está aqui / Destino)
            badge = m.get("badge") or ""
            if badge:
                c.setFont("Helvetica-Oblique", 7)
                c.setFillColor(self.palette["accent"])
                c.drawCentredString(cx, baseline_y - 28, badge)
    def wrap(self, *args):
        return (self.width, self.height)

class QRCodeFlowable(Flowable):
    def __init__(self, url, size_mm):
        super().__init__()
        self.url = url
        self.size = size_mm * mm
    def draw(self):
        qr = QrCodeWidget(self.url)
        # QR widget renders at its native size; scale via Drawing.
        bounds = qr.getBounds()
        w = bounds[2] - bounds[0]
        h = bounds[3] - bounds[1]
        d = Drawing(self.size, self.size, transform=[self.size/w, 0, 0, self.size/h, 0, 0])
        d.add(qr)
        renderPDF.draw(d, self.canv, 0, 0)
    def wrap(self, *args):
        return (self.size, self.size)

# ─── Page templates ──────────────────────────────────────────────────
PAGE_W, PAGE_H = A4

def cover_decorations(canvas, doc):
    canvas.saveState()
    # Top color bar
    canvas.setFillColor(P["primary"])
    canvas.rect(0, PAGE_H - 4*cm, PAGE_W, 4*cm, stroke=0, fill=1)
    # accent strip
    canvas.setFillColor(BRAND_RED)
    canvas.rect(0, PAGE_H - 4.25*cm, PAGE_W, 0.25*cm, stroke=0, fill=1)
    # Brand line at footer
    canvas.setFillColor(P["primary"])
    canvas.setFont("Helvetica", 9)
    canvas.drawString(2.5*cm, 1.5*cm, "Executive Education")
    today = datetime.date.today().strftime("%d/%m/%Y")
    canvas.drawRightString(PAGE_W - 2.5*cm, 1.5*cm, today)
    canvas.restoreState()

def content_decorations(canvas, doc):
    canvas.saveState()
    # Side accent bar
    canvas.setFillColor(P["accent"])
    canvas.rect(0, 0, 0.5*cm, PAGE_H, stroke=0, fill=1)
    # Footer
    canvas.setFillColor(HexColor("#6B7280"))
    canvas.setFont("Helvetica", 8)
    canvas.drawString(2.5*cm, 1.2*cm, "Plano de Carreira · " + NAME)
    canvas.drawRightString(PAGE_W - 2.5*cm, 1.2*cm, "pág. " + str(doc.page))
    canvas.restoreState()

# ─── Build document ──────────────────────────────────────────────────
out_path = "proposta-carreira-" + NAME.split()[0].lower().replace("/", "-") + ".pdf"
doc = BaseDocTemplate(
    out_path, pagesize=A4,
    leftMargin=2.5*cm, rightMargin=2.5*cm,
    topMargin=2*cm, bottomMargin=2*cm,
    title="Plano de Carreira — " + NAME,
    author="Executive Education")

cover_frame = Frame(2.5*cm, 2*cm, PAGE_W - 5*cm, PAGE_H - 4.5*cm, id="cover")
content_frame = Frame(2.5*cm, 2*cm, PAGE_W - 5*cm, PAGE_H - 4*cm, id="content")
doc.addPageTemplates([
    PageTemplate(id="Cover", frames=[cover_frame], onPage=cover_decorations),
    PageTemplate(id="Content", frames=[content_frame], onPage=content_decorations),
])

styles = getSampleStyleSheet()
H_AREA = ParagraphStyle("h_area", parent=styles["Normal"],
                        fontSize=11, leading=14, textColor=white,
                        alignment=TA_LEFT, spaceAfter=4)
H_TITLE = ParagraphStyle("h_title", parent=styles["Title"],
                         fontSize=36, leading=40, textColor=P["primary"],
                         alignment=TA_LEFT, spaceBefore=18, spaceAfter=6)
H_SUB = ParagraphStyle("h_sub", parent=styles["Normal"],
                       fontSize=18, leading=22, textColor=HexColor("#374151"),
                       alignment=TA_LEFT, spaceAfter=18)
H_SECTION = ParagraphStyle("h_section", parent=styles["Heading2"],
                           fontSize=14, leading=18, textColor=P["primary"],
                           spaceBefore=14, spaceAfter=8)
P_BODY = ParagraphStyle("body", parent=styles["BodyText"],
                        fontSize=10, leading=14, textColor=HexColor("#1F2937"),
                        alignment=TA_JUSTIFY, spaceAfter=8)
P_QUOTE = ParagraphStyle("quote", parent=styles["BodyText"],
                         fontSize=10, leading=14, textColor=HexColor("#374151"),
                         leftIndent=12, rightIndent=12,
                         fontName="Helvetica-Oblique", spaceAfter=12)
P_FOOT = ParagraphStyle("foot", parent=styles["Normal"],
                        fontSize=9, leading=12, textColor=HexColor("#6B7280"),
                        alignment=TA_CENTER)

story = []

# ─── PAGE 1 — COVER ──────────────────────────────────────────────────
story.append(Spacer(1, 0.5*cm))
story.append(Paragraph("Plano de Carreira", H_TITLE))
story.append(Paragraph("personalizado para <b>" + NAME + "</b>", H_SUB))
story.append(Spacer(1, 1*cm))
# Boxed callout: area selected
area_table = Table(
    [[Paragraph("Área escolhida", ParagraphStyle("k", parent=styles["Normal"],
                                                       fontSize=8, textColor=HexColor("#6B7280"), spaceAfter=4)),
      Paragraph("<b>" + AREA_LABEL + "</b>",
                ParagraphStyle("v", parent=styles["Normal"], fontSize=14,
                               textColor=P["primary"]))]],
    colWidths=[3.5*cm, None])
area_table.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), P["soft"]),
    ("BOX",        (0,0), (-1,-1), 0.5, P["accent"]),
    ("LEFTPADDING", (0,0), (-1,-1), 10),
    ("RIGHTPADDING", (0,0), (-1,-1), 10),
    ("TOPPADDING", (0,0), (-1,-1), 8),
    ("BOTTOMPADDING", (0,0), (-1,-1), 8),
]))
story.append(area_table)
story.append(Spacer(1, 1.5*cm))
story.append(Paragraph("<b>Cargo atual</b><br/>" + CARGO, P_BODY))
story.append(Paragraph("<b>Objetivo em 3 anos</b><br/>" + OBJETIVO, P_BODY))
story.append(Spacer(1, 2*cm))
story.append(Paragraph(
    "Este documento foi gerado a partir da conversa com a Marina " +
    "(consultora de carreira de Executive Education). Reflete sua escolha de área, " +
    "seu cargo atual e seu objetivo de 3 anos — e reúne os 3 programas " +
    "mais alinhados ao seu próximo passo.", P_BODY))

story.append(PageBreak())

# ─── PAGE 2 — EXECUTIVE SUMMARY + CAREER TIMELINE ────────────────────
story.append(Paragraph("Resumo executivo", H_SECTION))
if FINAL_PITCH:
    story.append(Paragraph(FINAL_PITCH, P_BODY))
else:
    # Aspas tipográficas curvas (U+201C / U+201D) ao redor do OBJETIVO em vez de
    # \\"…\\": o heredoc Groovy single-quoted que envolve este script processa
    # \\" como ", o que consumiria o backslash e quebraria a string Python gerada
    # com "" colado a + OBJETIVO. As mesmas aspas curvas já são usadas algumas
    # linhas abaixo em "“" + STAT_PITCH + "”".
    story.append(Paragraph(
        NAME + ", a partir do seu objetivo de “" + OBJETIVO +
        "” e da sua trajetória como " + CARGO +
        ", a trilha de " + AREA_LABEL + " é o caminho mais alinhado.", P_BODY))

if STAT_PITCH:
    story.append(Spacer(1, 0.4*cm))
    story.append(Paragraph("“" + STAT_PITCH + "”", P_QUOTE))

story.append(Spacer(1, 1*cm))
story.append(Paragraph("Sua trajetória — próximos 3 anos", H_SECTION))
if CAREER:
    timeline_w = PAGE_W - 5*cm
    story.append(CareerTimeline(CAREER, timeline_w, 3.2*cm, P))
else:
    story.append(Paragraph("(Sem mapa de carreira disponível.)", P_BODY))

story.append(PageBreak())

# ─── PAGE 3 — PROGRAM CARDS ──────────────────────────────────────────
story.append(Paragraph("Programas recomendados", H_SECTION))
if PROGRAMS:
    story.append(Paragraph(
        "Selecionamos 3 programas do catálogo Executive Education que se " +
        "encaixam no seu próximo passo. O programa do meio é o " +
        "mais alinhado ao seu objetivo.", P_BODY))
    story.append(Spacer(1, 0.4*cm))
    for i, prog in enumerate(PROGRAMS[:3]):
        is_main = (i == 1) and len(PROGRAMS) >= 2
        card_bg = P["soft"] if is_main else white
        card_border = P["primary"] if is_main else HexColor("#E5E7EB")
        name_color = P["primary"]
        tag = prog.get("tag", "")
        why = prog.get("why", "")
        if len(why) > 280: why = why[:277] + "..."
        header_text = "<b><font color='%s' size='12'>%s</font></b>" % (name_color.hexval(), prog.get("name", "-"))
        if tag:
            header_text += "<br/><font color='%s' size='8'><b>%s</b></font>" % (BRAND_RED.hexval(), tag.upper())
        meta_text = "<b>Duração:</b> %s &nbsp;·&nbsp; <b>Formato:</b> %s &nbsp;·&nbsp; <b>A partir de:</b> %s" % (
            prog.get("duration", "-"), prog.get("format", "-"), prog.get("priceFrom", "-"))
        card = Table([
            [Paragraph(header_text, P_BODY)],
            [Paragraph(meta_text, ParagraphStyle("meta", parent=P_BODY,
                                                  fontSize=9, textColor=HexColor("#374151")))],
            [Paragraph("<i>" + why + "</i>", ParagraphStyle("why", parent=P_BODY,
                                                             fontSize=9, textColor=HexColor("#4B5563")))],
        ], colWidths=[PAGE_W - 5*cm])
        card.setStyle(TableStyle([
            ("BACKGROUND",   (0,0), (-1,-1), card_bg),
            ("BOX",          (0,0), (-1,-1), 1.2 if is_main else 0.6, card_border),
            ("LEFTPADDING",  (0,0), (-1,-1), 12),
            ("RIGHTPADDING", (0,0), (-1,-1), 12),
            ("TOPPADDING",   (0,0), (-1,-1), 8),
            ("BOTTOMPADDING",(0,0), (-1,-1), 8),
        ]))
        story.append(card)
        story.append(Spacer(1, 0.3*cm))
else:
    story.append(Paragraph("(Sem programas disponíveis. Converse com um consultor.)", P_BODY))

story.append(PageBreak())

# ─── PAGE 4 — CLOSING + QR CODE ──────────────────────────────────────
story.append(Paragraph("Próximos passos", H_SECTION))
story.append(Paragraph(
    "1. <b>Visite ee.education.example.com</b> e revise os 3 programas em detalhe.<br/>" +
    "2. <b>Agende 15 minutos</b> com um consultor para mapear datas, formatos e bolsas.<br/>" +
    "3. <b>Compartilhe este plano</b> com seu(sua) gestor(a) ou parceiro(a) usando o QR Code abaixo.",
    P_BODY))

story.append(Spacer(1, 1*cm))

if SHARE_URL:
    qr_caption = Paragraph(
        "<b>Compartilhe seu plano</b><br/>" +
        "Quem escanear este QR cai num Programa-Match já pré-customizado<br/>" +
        "com seu nome, sua área e seu objetivo.",
        ParagraphStyle("qr_cap", parent=P_BODY, fontSize=9, textColor=HexColor("#4B5563")))
    qr_row = Table(
        [[QRCodeFlowable(SHARE_URL, size_mm=45), qr_caption]],
        colWidths=[5*cm, None])
    qr_row.setStyle(TableStyle([
        ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
        ("LEFTPADDING", (0,0), (-1,-1), 0),
        ("RIGHTPADDING", (0,0), (-1,-1), 12),
    ]))
    story.append(qr_row)
    story.append(Spacer(1, 0.6*cm))

story.append(Spacer(1, 1.5*cm))
story.append(Paragraph(
    "<i>Este plano foi gerado automaticamente pela Marina, consultora de IA " +
    "de Executive Education. As recomendações refletem o catálogo no momento da " +
    "geração. Para confirmação de preços, datas e bolsas, fale com um " +
    "consultor humano.</i>",
    P_FOOT))

# ─── Render ──────────────────────────────────────────────────────────
doc.build(story)
print("PDF gerado:", out_path)
print("Tamanho:", os.path.getsize(out_path), "bytes")
'''

// ─── Run it through the platform sandbox ──────────────────────────────
def result = code.executePython(pythonScript)

// Parse the Download URL from the markdown result. TurCodeInterpreterToolService
// emits exactly `[Download {filename}]({url})` for non-image files.
def downloadMatcher = (result =~ /\[Download\s+([^\]]+)\]\(([^)]+\.pdf)\)/)
if (downloadMatcher) {
    def filename = downloadMatcher[0][1]
    def url = downloadMatcher[0][2]
    slots.set("proposta_pdf_url", url)
    slots.set("proposta_pdf_filename", filename)
    // Persist the input fingerprint AFTER a successful run — falhas
    // intencionalmente NÃO escrevem o hash, pra que o próximo click
    // ainda tente regerar (cache só pinia sucessos).
    slots.set("proposta_pdf_hash", inputsHash)
    def name = (slots.get("name") ?: "")?.toString()
    def greeting = name ? "Pronto, ${name}!" : "Pronto!"
    return "✅ ${greeting} Seu **Plano de Carreira** está pronto — clique no botão **📄 Baixar Plano de Carreira** que apareceu abaixo dos programas para baixar."
}

// Failure path — surface the raw result so the persona can decide what to say.
// NOTE: hash NÃO é escrito aqui, garantindo que retry imediato vai re-rodar.
slots.set("proposta_pdf_url", "")
return "_(Não consegui gerar o PDF agora. Detalhe técnico: ${result?.take(400)})_"
