#!/usr/bin/env perl
# 把 dest.tsv 与 pending-*.tsv 合并去重，写回 dest.tsv，并删除 pending 文件。
# 去重规则：同 key 保留第一次出现；同一段中文文本只保留一个 key（后者并入前者时按文本去重）。
use strict;
use warnings;
use utf8;
binmode STDOUT, ':encoding(UTF-8)';

my $DEST = 'i18n/desktop/dest.tsv';
sub slurp { my $f = shift; open my $fh, '<:encoding(UTF-8)', $f or return ''; local $/; my $c = <$fh>; close $fh; $c }

my @lines = grep { /\S/ } split /\n/, slurp($DEST);
my $head = shift @lines;
my @head_cols = split /\t/, $head;

my (%row, %by_zh, %order);
my @src = ([@lines]);
for my $p (glob 'i18n/desktop/pending-*.tsv') {
    push @src, [grep { /\S/ } split /\n/, slurp($p)];
}

my $warn = 0;
for my $set (@src) {
    for my $line (@$set) {
        my @f = split /\t/, $line;
        my $k = shift @f;
        next unless defined $k and length $k;
        if (exists $row{$k}) {
            # 同 key：保留第一次，值不同则提示
            if (join("\t", @f) ne join("\t", @{ $row{$k} })) {
                print STDERR "同键不同值，保留第一次：$k\n"; $warn++;
            }
            next;
        }
        my $zh = $f[0] // '';
        if (length $zh and exists $by_zh{$zh}) {
            print STDERR "同一中文已有键 $by_zh{$zh}，跳过重复：$k\n"; $warn++;
            next;
        }
        $row{$k} = [@f];
        $by_zh{$zh} = $k if length $zh;
        $order{$k} = scalar keys %order;
    }
}

open my $o, '>:encoding(UTF-8)', $DEST or die $!;
print $o "$head\n";
for my $k (sort keys %row) {
    print $o join("\t", $k, @{ $row{$k} }), "\n";
}
close $o;

for my $p (glob 'i18n/desktop/pending-*.tsv') { unlink $p }
printf "已归一：%d 条（去重提示 %d 条），pending 文件已删除\n", scalar(keys %row), $warn;
