# Security Policy

This repository implements an actor with no credentials and no personal
data: the mock advisor and in-memory Store hold only synthetic plant/batch
fixtures, and the Governor never grants the actor direct write/actuation
authority (every proposal is `:effect :propose` only). If you find a
vulnerability -- including a Governor bypass that would let a proposal
commit, escalate incorrectly, or evade the closed op-allowlist -- report
privately to root@junkawasaki.com. Do not open public issues for security
reports.
