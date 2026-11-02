"use strict";

// Offline conversion only. Never connects to Firebase or overwrites a file.
const fs = require("node:fs");
const [inputFile, outputFile] = process.argv.slice(2);
if (!inputFile || !outputFile) {
  throw new Error("Usage: node scripts/migrate-restaurants.js users-export.json restaurants-import.json");
}
const users = JSON.parse(fs.readFileSync(inputFile, "utf8").replace(/^\uFEFF/, ""));
if (!users || Array.isArray(users) || typeof users !== "object" || users.users) {
  throw new Error("Export the /users node itself, not the entire database.");
}
const restaurants = {};
for (const [uid, user] of Object.entries(users)) {
  if (!user || user.role !== "SELLER") continue;
  // Explicit allowlist: no email, phone or deliveryAddressDetails.
  restaurants[uid] = {
    uid, role: "SELLER",
    name: typeof user.name === "string" ? user.name : "Restaurant",
    address: typeof user.address === "string" ? user.address : "",
    imageUrl: typeof user.imageUrl === "string" ? user.imageUrl : "",
    busy: user.busy === true,
    openingTime: typeof user.openingTime === "string" ? user.openingTime : "",
    closingTime: typeof user.closingTime === "string" ? user.closingTime : "",
    restaurantTimeZone: user.restaurantTimeZone || "Africa/Cairo"
  };
}
fs.writeFileSync(outputFile, JSON.stringify(restaurants, null, 2), { flag: "wx", mode: 0o600 });
console.log(`Prepared ${Object.keys(restaurants).length} restaurant records. Nothing uploaded.`);
