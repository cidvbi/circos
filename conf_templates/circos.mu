
karyotype={{folder}}/circos_data/karyotype.txt

chromosomes_order_by_karyotype = yes
chromosomes_units              = 1000
chromosomes_display_default    = yes

<<include {{folder}}/circos_configs/ideogram.conf>>
<<include {{folder}}/circos_configs/ticks.conf>>
<<include {{folder}}/circos_configs/plots.conf>>

<image>
<<include {{folder}}/circos_configs/image.conf>>
</image>

# includes etc/colors.conf
#          etc/fonts.conf
#          etc/patterns.conf
<<include etc/colors_fonts_patterns.conf>>

# system and debug settings
<<include etc/housekeeping.conf>>

anti_aliasing* = no
