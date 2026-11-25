# Collection zip format

A collection (a commitment of type **Collection**) can be filled from a zip file: open the
collection in the app, tap **Import zip**, and choose the file.

```
my-collection.zip
├── collection.json        optional: {"name": "My collection"}
├── items.csv              optional: group,name,image,collected
├── Group A/              a folder is a group
│   ├── First item.jpg     an image's file name (without the extension) is the item's name
│   └── Second_item.png    underscores become spaces: "Second item"
└── Loose item.jpg         images outside a folder go into a group named "Ungrouped"
```

## Rules

- **Images**: `.jpg`, `.jpeg`, `.png` or `.webp`. They are downscaled to 1280 px on the long side. PNGs
  stay PNG (so transparent backgrounds are kept); the rest are saved as JPEG. Anything else (and `__MACOSX/`, hidden files) is skipped and counted in the summary.
- **Groups**: one per top-level folder. Deeper folders belong to their top-level folder's group. If
  the whole zip sits inside one folder (as many zip tools do), that folder is looked through.
- **items.csv** (optional, UTF-8, header row required). Columns, in any order:

  | column      | meaning                                                                 |
  |-------------|-------------------------------------------------------------------------|
  | `group`     | group name; empty = the image's folder, or "Ungrouped"                  |
  | `name`      | item name; empty = the image's file name                                |
  | `image`     | path of the image inside the zip; empty = an item without a photo       |
  | `collected` | `1`, `true`, `yes`, `y` or `x` = already collected                     |

  Rows are kept in file order. A row needs a name or an image. Images the CSV doesn't mention are
  still added, from their folder and file name.
- **collection.json** (optional) only documents the collection's name; importing never renames it.
- **Importing again** into the same collection adds new groups and items. An item with the same name
  in the same group is updated (name, photo, collected) instead of added twice; a collected item
  stays collected.

## Example items.csv

```csv
group,name,image,collected
Group A,First item,Group A/First item.jpg,yes
Group A,Item without a photo,,
Group B,"Name, with a comma",Group B/photo.jpg,1
```
