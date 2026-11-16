#requires -Version 7.0
<##
Offline converter for an export of the ENTIRE Realtime Database.
No network requests, imports, deletion of source files, or credential access.
Usage: pwsh -File scripts/migrate-database-v2.ps1 -InputPath backup.json -OutputPath migrated.json
Keep exports and generated JSON outside version control.
##>
param(
    [Parameter(Mandatory)][string]$InputPath,
    [Parameter(Mandatory)][string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$inputFile = (Resolve-Path -LiteralPath $InputPath).Path
$outputFile = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $outputFile) { throw 'Output already exists. Choose a new filename.' }
$root = Get-Content -LiteralPath $inputFile -Raw | ConvertFrom-Json -AsHashtable
if ($root -isnot [System.Collections.IDictionary]) { throw 'Expected an entire database export object.' }
if ($root.Contains('rules')) { throw 'This looks like rules, not a database data export.' }
if (!$root.users) { $root.users = @{} }

function Get-Group([string]$role) {
    switch ($role.ToUpperInvariant()) {
        'BUYER' { 'buyers' }
        'SELLER' { 'sellers' }
        'RIDER' { 'riders' }
        'PENDING_RIDER' { 'riders' }
        default { throw "Unknown role '$role'. No output has been written. Review this account manually." }
    }
}
function Add-Unique($target, [string]$key, $value) {
    if ($target.Contains($key)) { throw "Duplicate record '$key'. No output has been written." }
    $target[$key] = $value
}

$groups = @{ buyers = @{}; sellers = @{}; riders = @{} }
$seen = @{}
foreach ($entry in $root.users.GetEnumerator()) {
    $accounts = if ($entry.Key -in @('buyers', 'sellers', 'riders')) {
        $entry.Value
    } else {
        @{ $entry.Key = $entry.Value }
    }
    foreach ($account in $accounts.GetEnumerator()) {
        $uid = [string]$account.Key
        $profile = $account.Value
        if ($profile -isnot [System.Collections.IDictionary]) { throw "Invalid profile: $uid" }
        $group = Get-Group ([string]$profile.role)
        if ($seen.Contains($uid)) { throw "Account '$uid' exists in multiple locations." }
        $seen[$uid] = $true
        $profile.uid = $uid
        if ($group -eq 'buyers') { $profile.role = 'BUYER' }
        if ($group -eq 'sellers') { $profile.role = 'SELLER' }
        $groups[$group][$uid] = $profile
    }
}
$root.users = $groups

# Keep discovery fields separate from private seller profiles.
$publicFields = @('uid', 'name', 'address', 'imageUrl', 'lat', 'lng', 'busy', 'openingTime', 'closingTime', 'restaurantTimeZone')
if (!$root.restaurants) { $root.restaurants = @{} }
foreach ($seller in $groups.sellers.GetEnumerator()) {
    $public = @{}
    foreach ($field in $publicFields) {
        if ($seller.Value.Contains($field)) { $public[$field] = $seller.Value[$field] }
    }
    $public.role = 'SELLER'
    # Preserve newer public fields already present in the export.
    if ($root.restaurants.Contains($seller.Key)) {
        foreach ($field in $publicFields) {
            if ($root.restaurants[$seller.Key].Contains($field)) {
                $public[$field] = $root.restaurants[$seller.Key][$field]
            }
        }
    }
    $public.uid = $seller.Key
    $root.restaurants[$seller.Key] = $public
}

foreach ($branch in @('menuItems', 'orders')) {
    $nested = @{}
    if ($root[$branch]) {
        foreach ($entry in $root[$branch].GetEnumerator()) {
            $records = if ($entry.Value.Contains('sellerId')) {
                @{ $entry.Key = $entry.Value }
            } else { $entry.Value }
            foreach ($record in $records.GetEnumerator()) {
                $sellerId = [string]$record.Value.sellerId
                if ([string]::IsNullOrWhiteSpace($sellerId)) { throw "Missing sellerId at $branch/$($record.Key)" }
                if (!$groups.sellers.Contains($sellerId)) { throw "Missing seller profile for $branch/$($record.Key): $sellerId" }
                if (!$entry.Value.Contains('sellerId') -and $entry.Key -ne $sellerId) {
                    throw "Restaurant path does not match sellerId at $branch/$($entry.Key)/$($record.Key)"
                }
                if (!$nested.Contains($sellerId)) { $nested[$sellerId] = @{} }
                $record.Value.id = $record.Key
                Add-Unique $nested[$sellerId] $record.Key $record.Value
            }
        }
    }
    $root[$branch] = $nested
}

$buyerIndex = @{}
foreach ($restaurant in $root.orders.GetEnumerator()) {
    foreach ($order in $restaurant.Value.GetEnumerator()) {
        $buyerId = [string]$order.Value.buyerId
        if ([string]::IsNullOrWhiteSpace($buyerId)) { throw "Missing buyerId in order $($order.Key)" }
        if (!$buyerIndex.Contains($buyerId)) { $buyerIndex[$buyerId] = @{} }
        Add-Unique $buyerIndex[$buyerId] $order.Key $restaurant.Key
    }
}
$root.buyerOrders = $buyerIndex
$json = ConvertTo-Json -InputObject $root -Depth 100
# CreateNew prevents overwriting even if the destination appears during conversion.
$stream = [IO.File]::Open($outputFile, [IO.FileMode]::CreateNew)
try {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json)
    $stream.Write($bytes, 0, $bytes.Length)
} finally { $stream.Dispose() }
Write-Output "Created $outputFile. Nothing was uploaded."
