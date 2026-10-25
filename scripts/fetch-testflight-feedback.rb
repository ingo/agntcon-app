#!/usr/bin/env ruby
# Fetches TestFlight tester feedback (screenshot and crash submissions) for an app from the App Store Connect API
# and writes it as JSON. Needs ASC_KEY_ID and ASC_ISSUER_ID, key at ~/.appstoreconnect/private_keys/AuthKey_<id>.p8
# (or ASC_KEY_PATH).
#
#   scripts/fetch-testflight-feedback.rb com.muschenetz.agntcon build/feedback.json
require "json"
require "net/http"
require "jwt"
require "openssl"
require "fileutils"

bundle_id, out = ARGV
abort "usage: #{$0} <bundle-id> <out.json>" unless bundle_id && out

key_id = ENV.fetch("ASC_KEY_ID")
issuer = ENV.fetch("ASC_ISSUER_ID")
key_path = ENV["ASC_KEY_PATH"] || File.expand_path("~/.appstoreconnect/private_keys/AuthKey_#{key_id}.p8")
key = OpenSSL::PKey::EC.new(File.read(key_path))
token = JWT.encode({ iss: issuer, iat: Time.now.to_i, exp: Time.now.to_i + 600, aud: "appstoreconnect-v1" }, key, "ES256", { kid: key_id })

def get(token, path, params = {})
  uri = URI("https://api.appstoreconnect.apple.com#{path}")
  uri.query = URI.encode_www_form(params) unless params.empty?
  res = Net::HTTP.start(uri.host, uri.port, use_ssl: true) do |http|
    http.request(Net::HTTP::Get.new(uri, "Authorization" => "Bearer #{token}"))
  end
  abort "#{path}: HTTP #{res.code} #{res.body}" unless res.is_a?(Net::HTTPSuccess)
  JSON.parse(res.body)
end

app = get(token, "/v1/apps", "filter[bundleId]" => bundle_id).fetch("data").first or abort "no app #{bundle_id}"

feedback = %w[betaFeedbackScreenshotSubmissions betaFeedbackCrashSubmissions].flat_map do |kind|
  get(token, "/v1/apps/#{app['id']}/#{kind}", "limit" => 200).fetch("data").map do |item|
    a = item["attributes"]
    {
      id: item["id"],
      kind: kind.include?("Crash") ? "crash" : "screenshot",
      created: a["createdDate"],
      comment: a["comment"],
      device: a["deviceModel"],
      os: a["osVersion"],
      build: a["buildBundleId"],
      screenshots: (a["screenshots"] || []).map { |s| s["url"] },
      raw: a
    }
  end
end

FileUtils.mkdir_p(File.dirname(out))
File.write(out, JSON.pretty_generate(feedback.sort_by { |f| f[:created].to_s }))
puts "#{feedback.size} feedback item(s) -> #{out}"
