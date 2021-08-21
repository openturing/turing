import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../service/sn-site.service';
import { ActivatedRoute } from '@angular/router';
import { TurLocale } from 'src/locale/model/locale.model';
import { TurLocaleService } from 'src/locale/service/locale.service';
import { TurSEInstance } from 'src/se/model/se-instance.model';
import { TurSEInstanceService } from 'src/se/service/se-instance.service';
import { TurNLPInstance } from 'src/nlp/model/nlp-instance.model';
import { TurNLPInstanceService } from 'src/nlp/service/nlp-instance.service';

@Component({
  selector: 'sn-site-page',
  templateUrl: './sn-site-page.component.html'
})
export class TurSNSitePageComponent implements OnInit {
  private turSNSite: Observable<TurSNSite>;
  private turLocales: Observable<TurLocale[]>;
  private turSEInstances: Observable<TurSEInstance[]>;
  private turNLPInstances: Observable<TurNLPInstance[]>;
  private id: string;
  constructor(private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turLocaleService: TurLocaleService,
    private turSEInstanceService: TurSEInstanceService,
    private turNLPInstanceService: TurNLPInstanceService,
    private route: ActivatedRoute) {
    this.turLocales = turLocaleService.query();
    this.turSEInstances = turSEInstanceService.query();
    this.turNLPInstances = turNLPInstanceService.query();
    this.id = this.route.snapshot.paramMap.get('id');
    this.turSNSite = this.turSNSiteService.get(this.id);

  }
  getId(): string {
    return this.id;
  }
  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  getTurSEInstances(): Observable<TurSEInstance[]> {

    return this.turSEInstances;
  }
  ngOnInit(): void {
  }

  getTurNLPInstances(): Observable<TurNLPInstance[]> {

    return this.turNLPInstances;
  }

  public saveSite(_turSNSite: TurSNSite) {
    this.turSNSiteService.save(_turSNSite).subscribe(
      (turSNSite: TurSNSite) => {
        _turSNSite = turSNSite;
        this.notifier.notify("success", turSNSite.name.concat(" semantic navigation site was updated."));
      },
      response => {
        this.notifier.notify("error", "SN site was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
