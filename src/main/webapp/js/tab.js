var genome_info_id = 87468;
var form_url = '/circos/home';
var ds_url = '//localhost:8888';

Ext.onReady(function() {
  Ext.create('Ext.form.Panel', {
    renderTo: 'circosPanel',
    border: false,
    layout: 'hbox',
    items: [{
      layout: {
        type: 'accordion',
        manageOverflow: 1,
        multi: true
      },
      width: 300,
      items: [{
        title: 'Default Tracks',
        bodyPadding: 5,
        items: [{
          xtype: 'hiddenfield',
          name: 'gid',
          value: genome_info_id
        }, {
          xtype: 'checkboxfield',
          boxLabel: 'CDS Forward',
          name: 'cds_forward',
          checked: true
        }, {
          xtype: 'checkboxfield',
          boxLabel: 'CDS Reverse',
          name: 'cds_reverse',
          checked: true
        }, {
          xtype: 'checkboxfield',
          boxLabel: 'RNA Forward',
          name: 'rna_forward'
        }, {
          xtype: 'checkboxfield',
          boxLabel: 'RNA Reverse',
          name: 'rna_reverse'
        }, {
          xtype: 'checkboxfield',
          boxLabel: 'Misc Forward',
          name: 'misc_forward'
        }, {
          xtype: 'checkboxfield',
          boxLabel: 'Misc Reverse',
          name: 'misc_reverse'
        },{
          xtype: 'checkboxfield',
          boxLabel: 'Include track for GC Content',
          name: 'gc_content',
          handler: function(self, checked) {
            self.findParentByType("panel").child("#gc_content_plot_type").setDisabled(!checked);
          }
        }, {
          xtype: 'combo',
          itemId: 'gc_content_plot_type',
          name: 'gc_content_plot_type',
          disabled: true,
          fieldLabel: 'Plot Type',
          valueField: 'value',
          editable: false,
          value: 'line',
          store: Ext.create('Ext.data.Store', {
            fields: ['text', 'value'],
            data: [{'text': 'Line Plot', 'value': 'line'}, {'text': 'Histogram', 'value': 'histogram'}, {'text': 'Heatmap', 'value': 'heatmap'}]
          })
        }, {
          xtype: 'checkboxfield',
          boxLabel: 'Include tracks for GC Skew',
          name: 'gc_skew',
          handler: function(self, checked) {
            self.findParentByType("panel").child("#gc_skew_plot_type").setDisabled(!checked);
          }
        }, {
          xtype: 'combo',
          itemId: 'gc_skew_plot_type',
          name: 'gc_skew_plot_type',
          disabled: true,
          fieldLabel: 'Plot Type',
          displayField: 'name',
          valueField: 'value',
          editable: false,
          value: 'line',
          store: Ext.create('Ext.data.Store', {
            fields: ['name', 'value'],
            data: [{'name': 'Line Plot', 'value':'line'}, {'name': 'Histogram', 'value':'histogram'}, {'name': 'Heatmap', 'value':'heatmap'}]
          })
        }]
      }, {
        title: 'Custom Tracks',
        bodyPadding: 5,
        items:[{
          xtype: 'button',
          text: '+',
          handler: addCustomTrack
        }, {
          id: 'custom_tracks',
          border: false,
          minHeight: 0,
          items:[]
        }]
      }, {
        title: 'Upload your own data files',
        bodyPadding: 5,
        items: [{
          xtype: 'button',
          text: '+',
          handler: addFileTrack
        }, {
          id: 'file_tracks',
          border: false,
          minHeight: 0,
          items: []
        }, {
          xtype: 'filefield',
          name: 'hidden_file_field',
          hidden: true
        }]
      }, {
        title: 'Image config',
        bodyPadding: 5,
        items: [{
          xtype: 'checkboxfield',
          boxLabel: 'Include outer track?',
          name: 'include_outer_track',
          checked: true
        }, {
          xtype: 'textfield',
          fieldLabel: 'Image Width/Height',
          name: 'image_dimensions',
          labelWidth: 150,
          width: 280,
          emptyText: '600'
        }, {
          xtype: 'sliderfield',
          fieldLabel: 'Track Width (1-10% of plot\'s radius)',
          name: 'track_width',
          labelWidth: 150,
          width: 280,
          value: 3,
          minValue: 1,
          maxValue: 10
        }]
      }, {
        title: '',
        items: [{
          xtype: 'button',
          text: 'Update Circos Graph',
          handler: function() {
            var form =  this.up('form').getForm();
            submitCircosRequest(form);
          }
        }]
      }]
    }, {
      id: 'graphPanel',
      overflowX: 'auto',
      overflowY: 'auto',
      width: '100%',
      minHeight: 600,
      contentEl:'circosGraph'
    }]
  });
});

function submitCircosRequest(form) {
  // console.log(form);
  if (form.isValid()) {
    Ext.get("circosGraph").mask('loading graph');
    form.submit({
      url: form_url,
      success: function(form, action) {
        loadCircosMap(action.result.imageId);
      }
    });
  }
}

function loadCircosMap(id) {
  Ext.Ajax.request({
    url: '../images/' + id + '/circos.html',
    success: function(rs) {
      Ext.get("circosGraph").unmask();
      var graph = Ext.getDom("circosGraph");
      graph.innerHTML = rs.responseText + '<img src="../images/' + id + '/circos.svg" usemap="#circosmap">';
    }
  });
}

// Variable to store current number of custom tracks
var customTrackCount = 0;
var fileTrackCount = 0;

function addCustomTrack() {
  var panelParent = Ext.getCmp("custom_tracks");

  var ct = Ext.create('Ext.panel.Panel', {
    layout: 'hbox',
    border: false,
    id: ('custom_track_' + customTrackCount),
    padding: '3 0 0 0',
    items: [{
      xtype: 'combo',
      name: ('custom_track_type_' + customTrackCount),
      displayField: 'name',
      valueField: 'value',
      editable: false,
      value: '',
      width: 90,
      store: Ext.create('Ext.data.Store', {
        fields: ['name', 'value'],
        data: [{'name': 'Type', 'value': ''}, {'name': 'CDS', 'value': 'cds'}, {'name': 'RNA', 'value': 'rna'}, {'name': 'Miscellaneous', 'value': 'misc'}]
      }),
      padding: '0 3 0 0',
      validator: function(value) {
        if (value === 'Type') {
          return false;
        } else {
          return true;
        }
      }
    }, {
      xtype: 'combo',
      name: ('custom_track_strand_' + customTrackCount),
      displayField: 'name',
      valueField: 'value',
      editable: false,
      value: '',
      width: 70,
      store: Ext.create('Ext.data.Store', {
        fields: ['name', 'value'],
        data: [{'name': 'Strand', 'value': ''}, {'name': 'Both', 'value': 'both'}, {'name': 'Forward', 'value': 'forward'}, {'name': 'Reverse', 'value': 'reverse'}]
      }),
      padding: '0 3 0 0',
      validator: function(value) {
        if (value === 'Strand') {
          return false;
        } else {
          return true;
        }
      }
    }, {
      xtype: 'textfield',
      name: ('custom_track_keyword_' + customTrackCount),
      emptyText: 'keyword',
      width: 100,
      padding: '0 3 0 0',
      validator: function(value) {
        if (value === '') {
          return false;
        } else {
          return true;
        }
      }
    }, {
      xtype: 'button',
      text: '-',
      handler: function() {
        var parent = this.findParentByType('panel');
        Ext.getCmp("custom_tracks").remove(parent.id);
      }
    }]
  });

  panelParent.insert(customTrackCount, ct);
  customTrackCount++;
}

function addFileTrack() {
  var panelParent = Ext.getCmp("file_tracks");

  var ct = Ext.create('Ext.panel.Panel', {
    layout: 'hbox',
    border: false,
    id: ('file_' + fileTrackCount),
    padding: '3 0 0 0',
    items: [{
      xtype: 'combo',
      name: ('file_plot_type_' + fileTrackCount),
      displayField: 'name',
      valueField: 'value',
      editable: false,
      value: 'tile',
      width: 80,
      store: Ext.create('Ext.data.Store', {
        fields: ['name', 'value'],
        data: [{'name': 'Tiles', 'value': 'tile'}, {'name': 'Line Plot', 'value': 'line'}, {'name': 'Histogram', 'value': 'histogram'}, {'name': 'Heatmap', 'value': 'heatmap'}]
      }),
      padding: '0 3 0 0'
    }, {
      xtype: 'filefield',
      name: ('file_' + fileTrackCount),
      width: 180,
      padding: '0 3 0 0'
    }, {
      xtype: 'button',
      text: '-',
      handler: function() {
        var parent = this.findParentByType('panel');
        Ext.getCmp("file_tracks").remove(parent.id);
      }
    }]
  });

  panelParent.insert(fileTrackCount, ct);
  fileTrackCount++;
}

function linkFeature(id) {
  Ext.Ajax.request({
    url: ds_url + '/dnafeature/' + id,
    success: function(rs) {
      createPopup(JSON.parse(rs.responseText));
    }
  });
}

function createPopup(feature) {
  var strFeature = Ext.String.format("{0}{1}{2}", feature.locus_tag, (feature.refseq_locus_tag!=null?" | "+feature.refseq_locus_tag:""), (feature.gene!=null?" | "+feature.gene:""));
  var strLoc = Ext.String.format("{0}: {1}..{2} ({3})", feature.feature_type, feature.start_max, feature.end_min, feature.strand);

  var linkFeature = Ext.String.format("Feature?cType=feature&cId={0}", feature.na_feature_id);
  var linkGB = Ext.String.format("GenomeBrowser?cType=feature&cId={0}&loc={1}..{2}&tracks=DNA,PATRICGenes", feature.na_feature_id, feature.start_max, feature.end_min);
  var linkCRV = Ext.String.format("CompareRegionViewer?cType=feature&cId={0}&tracks=&regions=5&window=10000&loc=1..10000", feature.na_feature_id);
  var linkPW = Ext.String.format("PathwayTable?cType=feature&cId={0}", feature.na_feature_id);
  var linkTR = Ext.String.format("TranscriptomicsGeneExp?cType=feature&cId={0}&sampleId=&colId=&log_ratio=&zscore=", feature.na_feature_id);
  var linkCR = Ext.String.format("TranscriptomicsGeneCorrelated?cType=feature&cId={0}", feature.na_feature_id);

  Ext.create('Ext.window.Window', {
    width: 500,
    border: false,
    bodyStyle: {'background-color': '#fff'},
    items: [{
      xtype: 'displayfield',
      value: '<h2>Feature Details</h2>',
      padding: 5
    }, {
      xtype: 'panel',
      border: false,
      items: [{
        html: '<a href="' + linkFeature + '">' + strFeature + '</a>',
        border: false,
        padding: '5 0 5 15'
      }, {
        xtype: 'displayfield',
        value: feature.product,
        padding: '5 0 5 15'
      }, {
        xtype: 'displayfield',
        value: strLoc,
        padding: '5 0 5 15'
      }]
    }, {
      xtype: 'displayfield',
      value: '<h2>For this feature, view:</h2>',
      padding: 5
    }, {
      xtype: 'panel',
      border: false,
      items: [{
        html: '<a href="' + linkGB + '">Genome Browser</a> &nbsp; <a href="' + linkCRV + '">Compare Region Viewer</a> &nbsp; <a href="' + linkPW + '">Pathways</a> &nbsp; <a href="' + linkTR + '">Transcriptomics Data</a> &nbsp; <a href="' + linkCR + '">Correlated genes</a>',
        padding: '5 0 5 15',
        style: {'line-height': '1.8em'},
        border: false
      }]
    }],
    buttons: [{
      text: 'OK',
      handler: function() {
        var w = this.up('window');
        w.close();
      }
    }]
  }).show();
}

var tooltipFired = false;
var tooltipTarget, tooltipId;
var tooltip;

function tooltipFeature(target, id) {
  tooltipTarget = target;
  tooltipId = id;

  if (tooltipFired !== false) {
    clearTimeout(tooltipFired);
  }
  tooltipFired = setTimeout(loadFeature, 500);
}

function loadFeature() {
  Ext.Ajax.request({
    url: ds_url + '/dnafeature/' + tooltipId,
    success: function(rs) {
      createTooltip(JSON.parse(rs.responseText));
    }
  });
}

function createTooltip(feature) {
  var strFeature = Ext.String.format("{0}{1}{2}", feature.locus_tag, (feature.refseq_locus_tag!=null?" | "+feature.refseq_locus_tag:""), (feature.gene!=null?" | "+feature.gene:""));
  var strLoc = Ext.String.format("{0}: {1} .. {2} ({3})", feature.feature_type, feature.start_max, feature.end_min, feature.strand);

  tooltip = Ext.create('Ext.tip.ToolTip', {
    target: tooltipTarget.id || tooltipTarget,
    anchor: 'top',
    trackMouse: true,
    html: '<p><b>' + strFeature + '</b><br/>' + feature.product + '<br/>' + strLoc + '<br/><i>Click for detail information</i></p>',
    tooltip: 10,
    bodyStyle: {'line-height': '2em'}
  });
}