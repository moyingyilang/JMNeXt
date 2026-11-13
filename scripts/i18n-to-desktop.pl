#!/usr/bin/env perl
# 读 desktop-keys.txt、dest.tsv、i18n/translations/*.tsv、values*/strings.xml，
# 生成 desktop/src/main/kotlin/com/jmnext/desktop/i18n/GeneratedStrings.kt。
use strict;
use warnings;
use utf8;
binmode STDOUT, ':encoding(UTF-8)';

my @LOCALES = qw(zh-CN zh-rTW zh-rHK en ja ru ko);
my $OUT = 'desktop/src/main/kotlin/com/jmnext/desktop/i18n/GeneratedStrings.kt';

sub slurp {
    my ($f) = @_;
    open my $fh, '<:encoding(UTF-8)', $f or return '';
    local $/;
    my $c = <$fh>;
    close $fh;
    return $c;
}

sub tsv_map {
    my ($f) = @_;
    my %m;
    for my $line (split /\n/, slurp($f)) {
        my ($k, $v) = split /\t/, $line, 2;
        next unless defined $k and defined $v;
        $m{$k} = $v;
    }
    return \%m;
}

sub xml_map {
    my ($f) = @_;
    my %m;
    my $c = slurp($f);
    while ($c =~ /<string name="([^"]*)">(.*?)<\/string>/gs) { $m{$1} = $2 }
    return \%m;
}

# 键清单 = desktop-keys.txt ∪ dest.tsv 里的键
my %have;
my @keys;
for my $k (grep { /\S/ } split /\n/, slurp('i18n/desktop/desktop-keys.txt')) {
    next if $have{$k}++;
    push @keys, $k;
}

# dest.tsv：第一行列名，其余每行 key + 各语言
my @dest_lines = grep { /\S/ } split /\n/, slurp('i18n/desktop/dest.tsv');
my @head = split /\t/, shift @dest_lines;
my %dest;
for my $line (@dest_lines) {
    my @f = split /\t/, $line;
    my $k = shift @f;
    next unless defined $k;
    for my $i (0 .. $#f) { $dest{$k}{$head[$i + 1]} = $f[$i] }
    next if $have{$k}++;
    push @keys, $k;
}

my %loc;
for my $l (@LOCALES) {
    $loc{$l} = ($l eq 'zh-rTW' || $l eq 'zh-rHK')
        ? xml_map("app/src/main/res/values-$l/strings.xml")
        : tsv_map("i18n/translations/$l.tsv");
}

sub value_of {
    my ($l, $k) = @_;
    return $dest{$k}{$l} if $k =~ /^dest_/;
    return $loc{$l}{$k};
}

sub kesc {
    my ($s) = @_;
    $s =~ s/\\/\\\\/g;
    $s =~ s/"/\\"/g;
    $s =~ s/\$/\\\$/g;
    return $s;
}

my @out;
push @out, 'package com.jmnext.desktop.i18n', '',
    '// 由 scripts/i18n-to-desktop.sh 生成，别手改',
    '// 键来自 i18n/desktop/desktop-keys.txt 与 i18n/desktop/dest.tsv', '',
    'internal val STRINGS: Map<String, Map<String, String>> = mapOf(';

for my $l (@LOCALES) {
    push @out, qq{    "$l" to mapOf(};
    for my $k (@keys) {
        my $v = value_of($l, $k);
        next unless defined $v and length $v;
        push @out, qq{        "$k" to "} . kesc($v) . qq{",};
    }
    push @out, '    ),';
}
push @out, ')', '',
    '/** 某门语言的表；缺语言时回落到简中。 */',
    'internal fun stringsFor(locale: String): Map<String, String> =',
    '    STRINGS[locale] ?: STRINGS.getValue("zh-CN")', '',
    '/** 中文原文 → 该语言的译文；桌面端现有文案以中文为键，便于逐屏替换。 */',
    'internal val BY_ZH: Map<String, Map<String, String>> = mapOf(';

my $cn = $loc{'zh-CN'};
for my $l (@LOCALES) {
    push @out, qq{    "$l" to mapOf(};
    my %seen;
    for my $k (@keys) {
        next if $k =~ /^dest_/;
        my $zh = $cn->{$k};
        my $v  = $loc{$l}{$k};
        next unless defined $zh and defined $v;
        next if $zh eq $v or $seen{$zh}++;
        push @out, qq{        "} . kesc($zh) . qq{" to "} . kesc($v) . qq{",};
    }
    for my $k (@keys) {
        next unless $k =~ /^dest_/;
        my $zh = $dest{$k}{'zh-CN'};
        my $v  = $dest{$k}{$l};
        next unless defined $zh and defined $v;
        next if $zh eq $v or $seen{$zh}++;
        push @out, qq{        "} . kesc($zh) . qq{" to "} . kesc($v) . qq{",};
    }
    push @out, '    ),';
}
push @out, ')', '',
    '/** 按中文原文取译文；没有对应译文就返回原文。 */',
    'internal fun byZhFor(locale: String): Map<String, String> =',
    '    BY_ZH[locale] ?: emptyMap()';

open my $o, '>:encoding(UTF-8)', $OUT or die "$OUT: $!";
print $o join("\n", @out), "\n";
close $o;
printf "已生成 %s（%d 键 × %d 语言）\n", $OUT, scalar(@keys), scalar(@LOCALES);
