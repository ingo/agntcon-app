// Mac prototype of the iOS screen clean-up (app/shared/src/iosMain/.../media/ScreenCleanUp.ios.kt), for tuning on
// real photos without a device. The Kotlin file is the source of truth; keep the two in step when tuning.
// Differences: the app also rejects slide outlines that are not screen-shaped (SCREEN_SHAPES), and evens out
// lighting on a 64 px copy instead of a full-size blur.
//
//   swiftc -O scripts/tuning/screen-cleanup.swift -o /tmp/screen-cleanup
//   /tmp/screen-cleanup <out-dir> photos/*.jpg      # prints what it found and the colour numbers per photo
//   python3 scripts/tuning/contact-sheet.py sheet.jpg photos/a.jpg <out-dir>/a.jpg ...   # before/after grid
//
// Note: the contact sheet (PIL) does not colour-manage, so Display P3 originals look off; convert them with
// `sips --matchTo "/System/Library/ColorSync/Profiles/sRGB Profile.icc"` before judging colours.

import CoreImage
import Vision
import Foundation

setvbuf(stdout, nil, _IONBF, 0)
let ctx = CIContext()
let args = CommandLine.arguments
let outDir = args[1]
let inputs = Array(args.dropFirst(2))

func area(_ q: VNRectangleObservation) -> Double {
    // shoelace on normalised corners
    let p = [q.topLeft, q.topRight, q.bottomRight, q.bottomLeft]
    var s = 0.0
    for i in 0..<4 { let a = p[i], b = p[(i + 1) % 4]; s += Double(a.x * b.y - b.x * a.y) }
    return abs(s) / 2
}

func small(_ path: String, _ px: CGFloat) -> CGImage {
    let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil)!
    return CGImageSourceCreateThumbnailAtIndex(src, 0, [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: px,
    ] as CFDictionary)!
}

func findScreen(_ img: CGImage) -> (VNRectangleObservation, String)? {
    let rect = VNDetectRectanglesRequest()
    rect.maximumObservations = 8
    rect.minimumConfidence = 0.5
    rect.minimumSize = 0.12
    rect.minimumAspectRatio = 0.3
    rect.quadratureTolerance = 30
    let doc = VNDetectDocumentSegmentationRequest()
    let h = VNImageRequestHandler(cgImage: img, options: [:])
    try? h.perform([rect, doc])
    let first = (rect.results ?? []).first { area($0) >= 0.06 }
    let d = doc.results?.first.flatMap { $0.confidence >= 0.85 && area($0) >= 0.06 ? $0 : nil }
    switch (first, d) {
    case let (b?, d?): return area(d) > area(b) * 1.3 ? (d, "doc") : (b, "rect")
    case let (b?, nil): return (b, "rect")
    case let (nil, d?): return (d, "doc")
    default: return nil
    }
}

func textLines(_ img: CGImage) -> Int {
    let r = VNDetectTextRectanglesRequest()
    try? VNImageRequestHandler(cgImage: img, options: [:]).perform([r])
    return r.results?.count ?? 0
}

/// Pixels of a small linear copy, as (r, g, b).
func pixels(_ image: CIImage) -> [(Double, Double, Double)] {
    let long = max(image.extent.width, image.extent.height)
    let s = image.applyingFilter("CILanczosScaleTransform", parameters: ["inputScale": 256 / long, "inputAspectRatio": 1])
    let w = Int(s.extent.width), h = Int(s.extent.height)
    var buf = [Float](repeating: 0, count: w * h * 4)
    ctx.render(s, toBitmap: &buf, rowBytes: w * 16, bounds: s.extent, format: .RGBAf, colorSpace: nil)
    return (0..<(w * h)).map { (Double(buf[$0 * 4]), Double(buf[$0 * 4 + 1]), Double(buf[$0 * 4 + 2])) }
}
func lum(_ p: (Double, Double, Double)) -> Double { 0.2126 * p.0 + 0.7152 * p.1 + 0.0722 * p.2 }
func pct(_ a: [Double], _ p: Double) -> Double { let s = a.sorted(); return s[min(s.count - 1, max(0, Int(Double(s.count) * p)))] }

/// The white of the slide is the least coloured of its brightest pixels: on a light slide that is the background, on
/// a dark one the text. Its tint is the colour cast, removed per channel. Then the brightest part is stretched to white
/// and the darkest to black with one black point for all channels, so nothing changes hue.
func balance(_ image: CIImage) -> (CIImage, String) {
    let px = pixels(image)
    let lums = px.map(lum)
    let bright = pct(lums, 0.9)
    let candidates = px.filter { lum($0) >= bright && lum($0) > 0.02 }
    func sat(_ p: (Double, Double, Double)) -> Double { let mx = max(p.0, p.1, p.2), mn = min(p.0, p.1, p.2); return mx > 0 ? (mx - mn) / mx : 0 }
    let neutral = candidates.sorted { sat($0) < sat($1) }.prefix(max(1, candidates.count / 3))
    let n = Double(neutral.count)
    var white = [neutral.map { $0.0 }.reduce(0, +) / n, neutral.map { $0.1 }.reduce(0, +) / n, neutral.map { $0.2 }.reduce(0, +) / n]
    // A cast is never stronger than this; past it the "white" is a coloured shape, and only part of it is corrected
    let top = white.max()!
    white = white.map { max($0, top / 2.5, 1e-4) }
    var tint = white.map { top / $0 }
    // Scale so the brightest 1.5 % reach white after the cast is removed
    let corrected = px.map { lum(($0.0 * tint[0], $0.1 * tint[1], $0.2 * tint[2])) }
    let hi = pct(corrected, 0.985), black = max(0, min(pct(corrected, 0.01), hi * 0.3))
    let k = 1 / max(hi - black, 0.02)
    let gains = tint.map { min($0 * k, 8) }
    tint = tint.map { $0 }
    let m = image.applyingFilter("CIColorMatrix", parameters: [
        "inputRVector": CIVector(x: gains[0], y: 0, z: 0, w: 0),
        "inputGVector": CIVector(x: 0, y: gains[1], z: 0, w: 0),
        "inputBVector": CIVector(x: 0, y: 0, z: gains[2], w: 0),
        "inputBiasVector": CIVector(x: -black * k, y: -black * k, z: -black * k, w: 0),
    ])
    let info = String(format: "white %.2f %.2f %.2f hi %.2f lo %.3f", white[0], white[1], white[2], hi, black)
    return (m.cropped(to: image.extent), info)
}
func median(_ image: CIImage) -> Double { pct(pixels(image).map(lum), 0.5) }

func enhance(_ flat: CIImage) -> (CIImage, String) {
    let (balanced, info) = balance(flat)
    let med = median(balanced)
    let light = med > 0.3  // linear ≈ 0.58 in sRGB
    var out = balanced
    if light {
        // Even out the projector's hot spot and fall-off, gently: a very wide blur, and never more than 1.4×
        let long = max(flat.extent.width, flat.extent.height)
        let illum = balanced.clampedToExtent()
            .applyingFilter("CIGaussianBlur", parameters: ["inputRadius": long / 6])
            .cropped(to: balanced.extent)
            .applyingFilter("CIColorClamp", parameters: ["inputMinComponents": CIVector(x: 0.7, y: 0.7, z: 0.7, w: 1), "inputMaxComponents": CIVector(x: 1, y: 1, z: 1, w: 1)])
        // divide: background ÷ input
        let even = illum.applyingFilter("CIDivideBlendMode", parameters: [kCIInputBackgroundImageKey: balanced])
        // Faint marks darker, white stays white
        out = even.applyingFilter("CIToneCurve", parameters: [
            "inputPoint0": CIVector(x: 0, y: 0),
            "inputPoint1": CIVector(x: 0.25, y: 0.17),
            "inputPoint2": CIVector(x: 0.5, y: 0.4),
            "inputPoint3": CIVector(x: 0.75, y: 0.68),
            "inputPoint4": CIVector(x: 1, y: 1),
        ])
    } else {
        out = balanced.applyingFilter("CIToneCurve", parameters: [
            "inputPoint0": CIVector(x: 0, y: 0),
            "inputPoint1": CIVector(x: 0.25, y: 0.22),
            "inputPoint2": CIVector(x: 0.5, y: 0.52),
            "inputPoint3": CIVector(x: 0.75, y: 0.8),
            "inputPoint4": CIVector(x: 1, y: 1),
        ])
    }
    if light { out = out.applyingFilter("CIVibrance", parameters: ["inputAmount": 0.15]) }
    out = out.applyingFilter("CIUnsharpMask", parameters: ["inputRadius": 2.0, "inputIntensity": 0.5])
    return (out, (light ? "light " : "dark ") + info + String(format: " med2 %.2f", med))
}

for path in inputs {
    let name = URL(fileURLWithPath: path).deletingPathExtension().lastPathComponent
    let detect = small(path, 1600)
    let image = CIImage(contentsOf: URL(fileURLWithPath: path), options: [.applyOrientationProperty: true])!
    let w = image.extent.width, h = image.extent.height
    var flat: CIImage
    var how: String
    if let (q, kind) = findScreen(detect) {
        let pts = [q.topLeft, q.topRight, q.bottomLeft, q.bottomRight]
        let cx = pts.map { $0.x }.reduce(0, +) / 4, cy = pts.map { $0.y }.reduce(0, +) / 4
        func v(_ p: CGPoint) -> CIVector { let i = 0.012; return CIVector(x: (p.x + (cx - p.x) * i) * w, y: (p.y + (cy - p.y) * i) * h) }
        flat = image.applyingFilter("CIPerspectiveCorrection", parameters: ["inputTopLeft": v(q.topLeft), "inputTopRight": v(q.topRight), "inputBottomLeft": v(q.bottomLeft), "inputBottomRight": v(q.bottomRight)])
        how = String(format: "%@ %.2f c%.2f", kind, area(q), q.confidence)
    } else {
        let lines = textLines(small(path, 1024))
        if lines < 4 { print("\(name): skip (text \(lines))"); continue }
        flat = image
        how = "text \(lines)"
    }
    let (out0, info) = enhance(flat)
    let out = out0.cropped(to: flat.extent)
    let long = max(out.extent.width, out.extent.height)
    let sized = long <= 3000 ? out : out.applyingFilter("CILanczosScaleTransform", parameters: ["inputScale": 3000 / long, "inputAspectRatio": 1])
    try! ctx.writeJPEGRepresentation(of: sized, to: URL(fileURLWithPath: "\(outDir)/\(name).jpg"), colorSpace: CGColorSpaceCreateDeviceRGB(), options: [:])
    print("\(name): \(how) \(info)")
}
