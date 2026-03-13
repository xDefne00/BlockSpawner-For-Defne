# BlockSpawner

Paper 1.21.8 plugin that allows players to place custom **block spawners** (using vanilla `SPAWNER` items) which generate item drops like diamonds, gold, and iron.

## Build

```bash
mvn clean package
```

Output jar: `target/BlockSpawner-1.0.0.jar`

## Commands

- `/blockspawner give <player> <type> <amount>` (admin)
- `/blockspawner reload` (admin)
- `/blockspawner remove <radius>` (admin)
- `/blockspawner list` (player)

## Config Example

```yaml
blockspawners:
  diamond:
    material: DIAMOND
    interval: 10
    amount: 2
  gold:
    material: GOLD_INGOT
    interval: 6
    amount: 2
  iron:
    material: IRON_INGOT
    interval: 4
    amount: 3
```
