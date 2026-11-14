// Optional supplement: install the datapack for example:gland and example:ice_ball.
BionicTraitEvents.definitions(event => {
  event.gameplay('create_biotech:egg_laying', {
    outputs: {egg: {item: 'minecraft:gold_nugget', min: 1, max: 2}}
  })
})
BionicGameplayEvents.produce('example:gland_secretion', event => {
  if (event.phase === 'before') event.outputs = [Item.of('minecraft:slime_ball', 2)]
})
BionicGameplayEvents.specialAttack('example:ice_ball', event => {
  if (event.phase === 'before' && event.entity.isInWater()) event.cancel()
})
BionicGameplayEvents.tame(event => {
  if (event.phase === 'before') event.amount = 0.8
})
