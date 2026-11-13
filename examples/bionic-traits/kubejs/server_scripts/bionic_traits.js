// KubeJS 2101.7.2-build.381+, Minecraft 1.21.1. Server scripts, reloaded by /reload.
BionicTraitEvents.definitions(event => {
  event.register('example:aquatic_regeneration', {
    schema_version: 1,
    display: {
      name: { text: '水生再生组织' },
      description: { text: '水中每秒回复少量生命；陆地每五秒回复一次。' }
    },
    scopes: ['whole_body'],
    value_kind: 'ability',
    inheritance: { type: 'presence' },
    automatic_detection: false,
    behaviors: [{ type: 'create_biotech:regeneration', interval_ticks: 100, amount: 1 }]
  })
  event.bind('example:cow_regeneration', {
    trait: 'example:aquatic_regeneration',
    values: { 'minecraft:cow': true }
  })
  event.modify('example:regeneration_balance', {
    trait: 'example:aquatic_regeneration',
    patch: { parameters: { pack_revision: 1 } }
  })
})

BionicTraitEvents.tick('example:aquatic_regeneration', event => {
  if (event.entity.tickCount % 20 === 0 && event.entity.isInWater()) event.entity.heal(0.25)
})

BionicTraitEvents.hurt('example:aquatic_regeneration', event => {
  if (event.entity.isInWater()) event.amount = event.amount * 0.8
})

// Successful melee callbacks only fire for traits carried by the actual attacking arm.
// BionicTraitEvents.melee('example:frost_arm', event => { ... })
