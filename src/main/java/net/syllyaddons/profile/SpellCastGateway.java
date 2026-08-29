package net.syllyaddons.profile;

@FunctionalInterface
public interface SpellCastGateway {
    SpellCastResult castSpell(int spellNumber);
}
