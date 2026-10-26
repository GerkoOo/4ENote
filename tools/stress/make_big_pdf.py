#!/usr/bin/env python3
"""Writes very large PDFs for stress testing 4ENote's PDF import (no PDF library needed; Pillow for scans).

    make_big_pdf.py pages <out.pdf> [pages]          many pages of text and lines (small file)
    make_big_pdf.py scans <out.pdf> [pages]          full-page noisy scans (big file, ~0.75 MB a page)
"""
import io, random, sys, zlib


def write_pdf(path, pages):
    """pages: list of (content_bytes, image_jpeg_bytes_or_None, (w, h) of the image)."""
    objects = []  # index i holds object number i+1

    def add(body):
        objects.append(body)
        return len(objects)

    font = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    pages_id = add(None)  # filled in below
    kids = []
    for content, jpeg, size in pages:
        resources = b"<< /Font << /F1 %d 0 R >>" % font
        if jpeg is not None:
            img = add(b"<< /Type /XObject /Subtype /Image /Width %d /Height %d /ColorSpace /DeviceRGB "
                      b"/BitsPerComponent 8 /Filter /DCTDecode /Length %d >>\nstream\n" % (size[0], size[1], len(jpeg))
                      + jpeg + b"\nendstream")
            resources += b" /XObject << /Im0 %d 0 R >>" % img
        resources += b" >>"
        packed = zlib.compress(content)
        stream = add(b"<< /Length %d /Filter /FlateDecode >>\nstream\n" % len(packed) + packed + b"\nendstream")
        kids.append(add(b"<< /Type /Page /Parent %d 0 R /MediaBox [0 0 595 842] /Resources %s /Contents %d 0 R >>"
                        % (pages_id, resources, stream)))
    objects[pages_id - 1] = b"<< /Type /Pages /Kids [%s] /Count %d >>" % (b" ".join(b"%d 0 R" % k for k in kids), len(kids))
    catalog = add(b"<< /Type /Catalog /Pages %d 0 R >>" % pages_id)

    with open(path, "wb") as f:
        f.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
        offsets = []
        for i, body in enumerate(objects, 1):
            offsets.append(f.tell())
            f.write(b"%d 0 obj\n" % i + body + b"\nendobj\n")
        xref = f.tell()
        f.write(b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1))
        for o in offsets:
            f.write(b"%010d 00000 n \n" % o)
        f.write(b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objects) + 1, catalog, xref))


def text_page(n):
    rnd = random.Random(n)
    lines = [b"BT /F1 28 Tf 60 780 Td (Stress page %d) Tj ET" % n]
    for row in range(40):
        words = b" ".join(b"lorem%d" % rnd.randint(0, 999) for _ in range(9))
        lines.append(b"BT /F1 10 Tf 60 %d Td (%s) Tj ET" % (750 - row * 17, words))
    for _ in range(60):  # some vector drawing too
        lines.append(b"%d %d m %d %d l S" % (rnd.randint(0, 595), rnd.randint(0, 842), rnd.randint(0, 595), rnd.randint(0, 842)))
    return b"\n".join(lines)


def scan_page(n):
    from PIL import Image
    w, h = 1240, 1754  # A4 at 150 dpi
    img = Image.effect_noise((w, h), 60).convert("RGB")
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=85)
    content = b"q 595 0 0 842 0 0 cm /Im0 Do Q\nBT /F1 28 Tf 60 780 Td (Scan %d) Tj ET" % n
    return content, buf.getvalue(), (w, h)


if __name__ == "__main__":
    kind, out = sys.argv[1], sys.argv[2]
    if kind == "pages":
        count = int(sys.argv[3]) if len(sys.argv) > 3 else 5000
        write_pdf(out, [(text_page(i + 1), None, None) for i in range(count)])
    else:
        count = int(sys.argv[3]) if len(sys.argv) > 3 else 400
        write_pdf(out, [scan_page(i + 1) for i in range(count)])
