import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from 'src/sn/model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from 'src/sn/service/sn-site.service';
import { ActivatedRoute } from '@angular/router';
import { TurSNSiteField } from 'src/sn/model/sn-site-field.model';

@Component({
  selector: 'sn-site-page',
  templateUrl: './sn-site-field-list-page.component.html'
})
export class TurSNSiteFieldListPageComponent implements OnInit {
  private turSNSite: Observable<TurSNSite>;
  private turSNSiteSEFields: Observable<TurSNSiteField[]>;
  private turSNSiteNLPFields: Observable<TurSNSiteField[]>;
  private siteId: string;
  constructor(private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent.parent.snapshot.paramMap.get('id');
    this.turSNSiteSEFields = turSNSiteService.getFieldsByType(this.siteId, "se");
    this.turSNSiteNLPFields = turSNSiteService.getFieldsByType(this.siteId, "ner");
    this.turSNSite = this.turSNSiteService.get(this.siteId);

  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteSEFields(): Observable<TurSNSiteField[]> {
    return this.turSNSiteSEFields;
  }

  getTurSNSiteNLPFields(): Observable<TurSNSiteField[]> {
    return this.turSNSiteNLPFields;
  }

  getId(): string {
    return this.siteId;
  }

  ngOnInit(): void {
  }

  public updateField(_turSNSiteField: TurSNSiteField, fieldName: string, event: Event) {
    _turSNSiteField[fieldName] = event ? 1 : 0;
    this.turSNSiteService.saveField(this.siteId, _turSNSiteField).subscribe(
      (turSNSiteField: TurSNSiteField) => {
        _turSNSiteField = turSNSiteField;
        this.notifier.notify("success", turSNSiteField.name.concat(" semantic navigation field was updated."));
      },
      response => {
        this.notifier.notify("error", "Semantic navigation field has a error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }
  public saveSite(_turSNSite: TurSNSite) {
    this.turSNSiteService.save(_turSNSite).subscribe(
      (turSNSite: TurSNSite) => {
        _turSNSite = turSNSite;
        this.notifier.notify("success", turSNSite.name.concat(" semantic navigation site was updated."));
      },
      response => {
        this.notifier.notify("error", "SN site has a error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
