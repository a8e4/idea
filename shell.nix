{ pkgs ? import <nixpkgs> { } }:

with pkgs;

let jre = jdk11;
    sbt = pkgs.sbt.override { inherit jre; };
in  stdenv.mkDerivation {
    name = "idea";
    version = "0.1.0.0";
    srcs = ./.;
    buildInputs = [ jre sbt ];
    shellHook = "export -p > .shell";
}
