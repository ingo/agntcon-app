// Compares the screen clean-up of two trip-report exports (the app's "Download report (.zip)", unzipped), or looks at
// one, with numbers and side-by-side sheets. Nothing of the app's logic is copied: it reads the pictures the app
// wrote, so it measures whatever build made them. To compare a change, export the report from the old build, install
// the new one (raising SCREEN_CLEAN_UP_VERSION redoes the photos in the background), export again, and compare.
//
//   swiftc -O scripts/tuning/compare-exports.swift -o /tmp/compare-exports
//   /tmp/compare-exports <out-dir> <export-A> [<export-B>] [--top N]
//
// Each export has media/<session>/NN-original.jpg next to NN.jpg (the cleaned copy); only photos where the app found
// a screen have the pair. Sheets show original | A | B, in the order of the biggest difference between A and B (or,
// with one export, the most cast left in A). The numbers, per picture, over its brightest quarter or all pixels:
//   cast     mean of (max - min) of R, G, B on the brightest 25% of pixels; 0 is neutral. White should be white.
//   mean     mean brightness, 0..1
//   clipped  share of pixels at 0.99 brightness or more (blown highlights; high is normal on white slides)
//   crushed  share of pixels at 0.02 or less (lost shadows)
// Pictures are drawn through ImageIO into sRGB, so Display P3 photos are colour-managed (unlike the PIL contact sheet).

import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

struct Stats { var cast = 0.0, mean = 0.0, clipped = 0.0, crushed = 0.0 }

func load(_ path: String, maxPixel: Int) -> CGImage? {
    guard let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil) else { return nil }
    return CGImageSourceCreateThumbnailAtIndex(src, 0, [
        kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: maxPixel] as CFDictionary)
}

func stats(_ image: CGImage) -> Stats? {
    let w = 200, h = max(1, 200 * image.height / image.width)
    var data = [UInt8](repeating: 0, count: w * h * 4)
    guard let ctx = CGContext(data: &data, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
    ctx.interpolationQuality = .medium
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
    var px = [(luma: Double, chroma: Double)]()
    for i in 0..<(w * h) {
        let r = Double(data[i * 4]) / 255, g = Double(data[i * 4 + 1]) / 255, b = Double(data[i * 4 + 2]) / 255
        px.append((0.2126 * r + 0.7152 * g + 0.0722 * b, max(r, g, b) - min(r, g, b)))
    }
    px.sort { $0.luma > $1.luma }
    let top = px.prefix(max(1, px.count / 4))
    let n = Double(px.count)
    return Stats(cast: top.map { $0.chroma }.reduce(0, +) / Double(top.count),
                 mean: px.map { $0.luma }.reduce(0, +) / n,
                 clipped: Double(px.filter { $0.luma >= 0.99 }.count) / n,
                 crushed: Double(px.filter { $0.luma <= 0.02 }.count) / n)
}

func sheet(_ images: [CGImage], height: Int = 420) -> CGImage? {
    let widths = images.map { max(1, $0.width * height / $0.height) }
    let total = widths.reduce(0, +) + 8 * (images.count - 1)
    guard let ctx = CGContext(data: nil, width: total, height: height, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
    ctx.setFillColor(CGColor(gray: 0.5, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: total, height: height))
    var x = 0
    for (i, image) in images.enumerated() { ctx.draw(image, in: CGRect(x: x, y: 0, width: widths[i], height: height)); x += widths[i] + 8 }
    return ctx.makeImage()
}

func writeJPEG(_ image: CGImage, to url: URL) {
    guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return }
    CGImageDestinationAddImage(dest, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
    CGImageDestinationFinalize(dest)
}

func median(_ v: [Double]) -> Double { v.isEmpty ? 0 : v.sorted()[v.count / 2] }
func f2(_ x: Double) -> String { String(format: "%.2f", x) }

var args = Array(CommandLine.arguments.dropFirst())
var top = 24
if let i = args.firstIndex(of: "--top"), i + 1 < args.count { top = Int(args[i + 1]) ?? 24; args.removeSubrange(i...(i + 1)) }
guard args.count >= 2 else {
    print("usage: compare-exports <out-dir> <export-A> [<export-B>] [--top N]"); exit(1)
}
let out = URL(fileURLWithPath: args[0]), a = args[1], b: String? = args.count > 2 ? args[2] : nil
try? FileManager.default.createDirectory(at: out.appendingPathComponent("sheets"), withIntermediateDirectories: true)

func pairs(in export: String) -> [String: (original: String, cleaned: String)] {
    var found = [String: (String, String)]()
    let media = URL(fileURLWithPath: export).appendingPathComponent("media")
    guard let walker = FileManager.default.enumerator(at: media, includingPropertiesForKeys: nil) else { return found }
    for case let url as URL in walker where url.lastPathComponent.hasSuffix("-original.jpg") {
        let cleaned = url.path.replacingOccurrences(of: "-original.jpg", with: ".jpg")
        guard FileManager.default.fileExists(atPath: cleaned) else { continue }
        let key = url.deletingLastPathComponent().lastPathComponent + "/" + url.lastPathComponent.replacingOccurrences(of: "-original.jpg", with: "")
        found[key] = (url.path, cleaned)
    }
    return found
}

let pairsA = pairs(in: a), pairsB = b.map(pairs(in:)) ?? [:]
let keys = (b == nil ? Array(pairsA.keys) : pairsA.keys.filter { pairsB[$0] != nil }).sorted()
print("\(keys.count) photos with a cleaned copy" + (b == nil ? "" : " in both exports") + "; A has \(pairsA.count)" + (b == nil ? "" : ", B has \(pairsB.count)"))

struct Row { let key: String; let o: Stats; let a: Stats; let b: Stats? }
var rows = [Row]()
var csv = "photo,cast_original,cast_A,cast_B,mean_original,mean_A,mean_B,clipped_A,clipped_B,crushed_A,crushed_B\n"
for key in keys {
    guard let pa = pairsA[key], let o = load(pa.original, maxPixel: 640).flatMap(stats), let sa = load(pa.cleaned, maxPixel: 640).flatMap(stats) else { continue }
    let sb = pairsB[key].flatMap { load($0.cleaned, maxPixel: 640) }.flatMap(stats)
    rows.append(Row(key: key, o: o, a: sa, b: sb))
    csv += "\(key),\(f2(o.cast)),\(f2(sa.cast)),\(sb.map { f2($0.cast) } ?? ""),\(f2(o.mean)),\(f2(sa.mean)),\(sb.map { f2($0.mean) } ?? ""),\(f2(sa.clipped)),\(sb.map { f2($0.clipped) } ?? ""),\(f2(sa.crushed)),\(sb.map { f2($0.crushed) } ?? "")\n"
}
try? csv.write(to: out.appendingPathComponent("results.csv"), atomically: true, encoding: .utf8)

// Groups by how bright the (A) result is: a stand-in for dark / light slides that needs no classifier
func group(_ r: Row) -> String { r.a.mean < 0.30 ? "dark   (A mean < 0.30)" : r.a.mean > 0.55 ? "light  (A mean > 0.55)" : "middle (0.30 to 0.55)" }
print("\nmedians per group\n" + "group".padding(toLength: 24, withPad: " ", startingAt: 0) + "     n | cast orig    A     B | clipped A     B | crushed A     B")
for name in Set(rows.map(group)).sorted() {
    let g = rows.filter { group($0) == name }
    let row = "\(name.padding(toLength: 24, withPad: " ", startingAt: 0)) \(String(format: "%5d", g.count)) |      \(f2(median(g.map { $0.o.cast })))  \(f2(median(g.map { $0.a.cast })))  \(g.allSatisfy { $0.b == nil } ? "  - " : f2(median(g.compactMap { $0.b?.cast }))) |      \(f2(median(g.map { $0.a.clipped })))  \(g.allSatisfy { $0.b == nil } ? "  - " : f2(median(g.compactMap { $0.b?.clipped }))) |      \(f2(median(g.map { $0.a.crushed })))  \(g.allSatisfy { $0.b == nil } ? "  - " : f2(median(g.compactMap { $0.b?.crushed })))"
    print(row)
}

let ranked = rows.sorted { l, r in
    let dl = l.b.map { abs($0.cast - l.a.cast) + abs($0.mean - l.a.mean) } ?? l.a.cast
    let dr = r.b.map { abs($0.cast - r.a.cast) + abs($0.mean - r.a.mean) } ?? r.a.cast
    return dl > dr
}.prefix(top)
for (rank, row) in ranked.enumerated() {
    guard let pa = pairsA[row.key], let o = load(pa.original, maxPixel: 900), let ca = load(pa.cleaned, maxPixel: 900) else { continue }
    var panels = [o, ca]
    if let pb = pairsB[row.key], let cb = load(pb.cleaned, maxPixel: 900) { panels.append(cb) }
    if let s = sheet(panels) {
        writeJPEG(s, to: out.appendingPathComponent("sheets/\(String(format: "%02d", rank + 1))_\(row.key.replacingOccurrences(of: "/", with: "_")).jpg"))
    }
}
print("\nwrote \(out.path)/results.csv and \(ranked.count) sheets in \(out.path)/sheets (original | A" + (b == nil ? ")" : " | B)"))
