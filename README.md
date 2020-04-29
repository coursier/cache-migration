## cache-migration

A CLI tool to migrate a legacy coursier cache, under `~/.coursier/cache`,
to the newer OS-dependent location:
- `~/.cache/coursier` on Linux,
- `~/Library/Caches/Coursier` on macOS,
- `C:\Users\_UserName_\AppData\Local\Coursier\Cache` in most cases on Windows (replace `_UserName_` by your user name - for example, user name `Alex` gives `C:\Users\Alex\AppData\Local\Coursier\Cache`).

### Linux / macOS instructions

Run a dry run with
```bash
$ cs launch --contrib cache-migration -- --dry-run
```

Really run it with
```bash
$ cs launch --contrib cache-migration
```

Optionally, pass `--clean-up` to remove `~/.coursier/cache`, if it's empty
after the migration
```bash
$ cs launch --contrib cache-migration -- --clean-up
```

If both the legacy and the newer cache directories exist on your system,
a message will invite you to pass some extra options. As of writing this,
these options are:
- `--one-by-one` (individually moving files from the legacy cache to the newer one) and
- `--clean-up` (removing empty directories from the legacy cache, and the legacy cache itself if it ends up being empty).

You should then run `cache-migration` like
```bash
$ cs launch --contrib cache-migration -- --one-by-one --clean-up
```

### Windows

Create a launcher for cache-migration with
```bash
cs bootstrap --standalone --contrib cache-migration -o cache-migration
```

This creates two files in the current directory:
- `cache-migration`, which is a JAR for cache-migration, and
- `cache-migration.bat`, which launches `cache-migration`.

Then follow the Linux and macOS instructions above, replacing `cs launch --contrib cache-migration --` with `.\cache-migration`, like
```bash
.\cache-migration --dry-run
```

```bash
.\cache-migration
```

```bash
.\cache-migration --clean-up
```

```bash
.\cache-migration --one-by-one --clean-up
```
