#!/usr/bin/env perl
# 打印 key<TAB>格式占位符列表，供 i18n-check.sh 比对。
my $file = shift or die "用法: i18n-specs.pl <strings.xml>\n";
open my $fh, '<', $file or die $!;
while (<$fh>) {
    next unless /<string name="([^"]*)">(.*)<\/string>/;
    my ($k, $v) = ($1, $2);
    my @specs = $v =~ /(%\d+\$[sd])/g;
    print "$k\t", join(',', @specs), "\n";
}
